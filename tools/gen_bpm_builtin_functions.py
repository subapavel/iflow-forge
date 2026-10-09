#!/usr/bin/env python3
"""Generate the NW BPM built-in function catalog (MOF ID -> function) from an NWDS installation.

Sources (NWDS plugins dir):
  com.sap.tc.ap.servicecomp_*.jar -> DC sap.com/tc/bpem/content/predefs/types
      rep/functions/sap.com/bpem/glx/built-in/<category>/*.function   (operators, text, numeric, date-time, generic ...)
      rep/wsdl/xml_schema.types                                       (XSD built-in types)
  com.sap.tc.ap.bpemcontent_*.jar -> DC sap.com/tc/bpem/content/predefs/default
      src/functions/**/*.function                                     (principal functions, getExternalUser)
      src/triggers/*.eventdef                                         (standard triggers: TechnicalError, deadlines,
                                                                       escalations, default message/timer triggers)

Usage: python3 gen_bpm_builtin_functions.py <nwds-plugins-dir> <output.json>
Output is derived from SAP software: keep it in private repositories only.
"""
import glob, io, json, os, re, sys, zipfile
import xml.etree.ElementTree as ET
from collections import defaultdict

MOIN = '{http://xml.sap.com/moin}'
# (plugin jar glob, inner DC archive suffix, path prefix of .function files inside the DC archive)
SOURCES = [
    ('com.sap.tc.ap.servicecomp_*.jar', 'sap.com~tc~bpem~content~predefs~types~def_cmp.zip', 'rep/functions/'),
    ('com.sap.tc.ap.bpemcontent_*.jar', 'sap.com~tc~bpem~content~predefs~default~def_cmp.zip', 'src/functions/'),
]


def load_partition(data):
    """Return (elements {id: (metaclass, attrs)}, links [(assoc, f, s)])."""
    root = ET.fromstring(data)
    el, links = {}, []
    for e in root:
        if e.tag in ('C', 'M'):
            continue
        if MOIN + 'i' in e.attrib:
            d = {k: v for k, v in e.attrib.items() if not k.startswith(MOIN)}
            for c in e:
                if c.tag in ('name', 'documentation'):
                    d[c.tag] = c.get('originalText')
            el[e.attrib[MOIN + 'i']] = (e.tag.split('.')[-1], d)
        else:
            for l in e:
                links.append((e.tag.split('.')[-1], l.get('f').replace('local:', ''), l.get('s').replace('local:', '')))
    return el, links


def open_sources(plugins_dir):
    """Yield (plugin file name, DC name, ZipFile of the DC archive, function path prefix)."""
    for jar_glob, inner_suffix, prefix in SOURCES:
        jars = sorted(glob.glob(os.path.join(plugins_dir, jar_glob)))
        if not jars:
            sys.exit(jar_glob + ' not found in ' + plugins_dir)
        outer = zipfile.ZipFile(jars[-1])
        inner_name = next(n for n in outer.namelist() if n.endswith(inner_suffix))
        dc = inner_suffix.replace('~def_cmp.zip', '').replace('~', '/')
        yield os.path.basename(jars[-1]), dc, zipfile.ZipFile(io.BytesIO(outer.read(inner_name))), prefix


def read_types(dc_zips):
    """Return (xsd, sap): XSD built-in types (ID -> name) from xml_schema.types, and SAP-defined
    types from the other rep/wsdl/*.types of the source DCs (e.g. 'Principal' in sap.com.glx.types)."""
    xsd, sap = {}, {}
    for dc_zip in dc_zips:
        for n in dc_zip.namelist():
            if not (n.startswith('rep/wsdl/') and n.endswith('.types')):
                continue
            target = xsd if n.endswith('/xml_schema.types') else sap
            t = dc_zip.read(n).decode('utf-8')
            for m in re.finditer(r"<XSD1\.Xsd(?:Simple|Complex)TypeDefinition[^>]*m:i='([0-9A-F]{40})'[^>]*>", t):
                nm = re.search(r"name='([^']*)'", m.group(0))
                if nm:
                    target.setdefault(m.group(1), nm.group(1))
    for k in xsd:
        sap.pop(k, None)
    return xsd, sap


def read_functions(data, name, xsd, plugin, dc):
    el, links = load_partition(data)
    adj = defaultdict(set)
    by_assoc = defaultdict(list)
    for a, f, s in links:
        by_assoc[a].append((f, s))
        if a in ('FunctionInterfaceHasFormalParameter', 'FunctionInterfaceHasResult', 'FunctionInterfaceHasFunctions'):
            continue
        adj[f].add((s, a))
        adj[s].add((f, a))

    # parameter order: ParticlesForModelGroup is an ordered association (link order = sequence order)
    particle_pos = {}
    for idx, (f, s) in enumerate(by_assoc.get('ParticlesForModelGroup', [])):
        particle_pos[f] = idx
        particle_pos[s] = idx
    elem_pos = {}
    for f, s in by_assoc.get('ContentForParticle', []):
        for part, elem in ((f, s), (s, f)):
            if part in particle_pos:
                elem_pos[elem] = particle_pos[part]

    def type_of(decl):
        tp = [z for z, a in adj[decl] if a == 'TypeDefinitionForElementDeclaration']
        return tp[0] if tp else None

    def fields(wrapper):
        """Element declarations inside the wrapper element's (request/response) complex type."""
        type_id = type_of(wrapper)
        if not type_id:
            return []
        res, seen, stack = [], {type_id, wrapper}, [type_id]
        while stack:
            x = stack.pop()
            for y, a in adj[x]:
                if y in seen or y not in el:
                    continue
                seen.add(y)
                t2, d = el[y]
                if t2 == 'XsdElementDeclaration':
                    tid = type_of(y)
                    res.append({'name': d.get('name'), 'type': xsd.get(tid, tid), '_pos': elem_pos.get(y, 999)})
                else:
                    stack.append(y)
        res.sort(key=lambda r: r.pop('_pos'))
        return res

    def ends(assoc, owner):
        return [f for f, s in by_assoc.get(assoc, []) if s == owner] or \
               [s for f, s in by_assoc.get(assoc, []) if f == owner]

    out = []
    for fid, (mc, d) in el.items():
        if mc != 'FunctionInterface':
            continue
        req = [x for x in ends('FunctionInterfaceHasFormalParameter', fid) if x in el]
        resp = [x for x in ends('FunctionInterfaceHasResult', fid) if x in el]
        java = [el[x][1].get('className') for x in ends('FunctionInterfaceHasFunctions', fid) if x in el]
        result = fields(resp[0]) if resp else []
        ns = d.get('namespace', '')
        out.append({
            'id': fid,
            'namespace': ns,
            'category': ns.rsplit('/', 1)[-1],
            'name': d.get('name'),
            'symbol': d.get('symbol'),
            'infix': d.get('infix') == 'true',
            'documentation': d.get('documentation'),
            'parameters': fields(req[0]) if req else [],
            'result': result[0]['type'] if result else None,
            'javaClass': java[0] if java else None,
            'file': name,
            'sourcePlugin': plugin,
            'sourceDc': dc,
        })
    return out


EVENT_DEFINITIONS = {'ErrorEventDefinition': 'error', 'MessageEventDefinition': 'message',
                     'TimerEventDefinition': 'timer'}


def read_triggers(data, name, types, plugin, dc):
    """Standard event definitions of an .eventdef partition (one file may hold several, e.g. a message
    trigger with its fault). Parameter = XSD element linked via EventDefinitionHasParameter."""
    el, links = load_partition(data)
    param_of = {s: f for a, f, s in links if a == 'EventDefinitionHasParameter'}
    type_of = {f: s for a, f, s in links if a == 'TypeDefinitionForElementDeclaration'}
    out = []
    for tid, (mc, d) in el.items():
        if mc not in EVENT_DEFINITIONS:
            continue
        p = param_of.get(tid)
        parameter = None
        if p in el:
            pd = el[p][1]
            type_id = type_of.get(p)
            parameter = {'name': pd.get('name'), 'namespace': pd.get('namespace'),
                         'type': types.get(type_id, type_id)}
        out.append({
            'id': tid,
            'kind': EVENT_DEFINITIONS[mc],
            'name': d.get('name'),
            'documentation': d.get('documentation'),
            'isCritical': d.get('isCritical') == 'true',
            'isTechnical': d.get('isTechnical') == 'true',
            'parameter': parameter,
            'file': name,
            'sourcePlugin': plugin,
            'sourceDc': dc,
        })
    return out


def main(plugins_dir, out_path):
    sources = list(open_sources(plugins_dir))
    xsd, sap = read_types([s[2] for s in sources])
    all_types = {**sap, **xsd}
    functions, triggers = [], []
    for plugin, dc, dc_zip, prefix in sources:
        for name in sorted(n for n in dc_zip.namelist() if n.startswith(prefix) and n.endswith('.function')):
            functions.extend(read_functions(dc_zip.read(name), name, all_types, plugin, dc))
        for name in sorted(n for n in dc_zip.namelist() if n.startswith('src/triggers/') and n.endswith('.eventdef')):
            triggers.extend(read_triggers(dc_zip.read(name), name, all_types, plugin, dc))
    functions.sort(key=lambda o: (o['category'], o['name'], o['id']))
    triggers.sort(key=lambda o: (o['kind'], o['name'], o['id']))
    out = {
        # 'source' = primary source (object, read by BuiltinFunctionCatalog); further sources listed separately
        'source': {'plugin': sources[0][0], 'dc': sources[0][1]},
        'additionalSources': [{'plugin': s[0], 'dc': s[1]} for s in sources[1:]],
        'functions': functions,
        'triggers': triggers,
        'xsdTypes': dict(sorted(xsd.items())),
        'sapTypes': dict(sorted(sap.items())),
    }
    with open(out_path, 'w', encoding='utf-8', newline='\n') as fh:  # LF on every platform
        json.dump(out, fh, indent=1, ensure_ascii=False)
    print(f'{len(functions)} functions, {len(triggers)} triggers, {len(xsd)} XSD types, '
          f'{len(sap)} SAP types -> {out_path}')


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
