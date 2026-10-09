import sys,os,glob,re
sys.path.insert(0,os.path.dirname(__file__))
from moin import load
from collections import defaultdict
root,main=sys.argv[1],sys.argv[2]
G={}
for p in glob.glob(root+'/**/*',recursive=True):
    if os.path.isfile(p) and b'<Partition' in open(p,'rb').read(200):
        for k,v in load(p)[0].items(): G[k]=v
BUILTIN={}  # from compiled csv: id -> java class short
for c in glob.glob(os.path.join(os.path.dirname(os.path.abspath(root)),'dep/bpem/gen_bpm/bpmn/*.csv')):
    t=open(c,encoding='utf-8',errors='replace').read()
    for m in re.finditer(r'built-in/(\w+)/([0-9A-F]{40})',t):
        cls=re.findall(r'class="([\w.$]+)"',t[max(0,m.start()-600):m.start()])
        if cls: BUILTIN.setdefault(m.group(2),cls[-1].split('.')[-1].replace('Function','')+'@'+m.group(1))
el,A=load(main)
def r(x): return x.replace('local:','')
fw=defaultdict(lambda: defaultdict(list)); bw=defaultdict(lambda: defaultdict(list))
for a,ls in A.items():
    a=a.split('.',1)[1]
    for f,s in ls: fw[a][r(s)].append(r(f)); bw[a][r(f)].append(r(s))
def T(i): return (el.get(i) or G.get(i) or ('?',{}))
def nm(i):
    t,d=T(i); return d.get('name') or t.split('.')[-1]
def qn(i):  # QName attached
    q=fw['Rule.ReferenceHasQName'].get(i) or fw['Mapping.MappingPartHasQName'].get(i)
    return T(q[0])[1].get('name') if q else None
def expr(e,depth=0):
    if depth>6: return '…'
    out=[]
    for st in fw['Rule.ExpressionHasSteps'].get(e,[]):
        t,d=T(st); k=t.split('.')[-1]
        if k=='Reference': out.append(('/' if out else '')+(qn(st) or '?'))
        elif k=='Literal': out.append(repr(d.get('value')))
        elif k=='FunctionInvocation':
            fi=fw['Rule.FunctionInvocationHasFunctionInterface'].get(st,[None])[0]
            fname=(T(fi)[1].get('name') if fi in G or fi in el else None) or BUILTIN.get(fi,'fn?'+str(fi)[:6])
            args=[]
            for mp in fw['Rule.FunctionInvocationHasParameterMapping'].get(st,[]):
                for part in fw['Mapping.MappingContainsMappingParts'].get(mp,[]):
                    for ex in fw['Mapping.MappingPartContainsExpression'].get(part,[]):
                        args.append(f"{qn(part)}={expr(ex,depth+1)}")
            out.append(('/' if out else '')+f"{fname}({', '.join(args)})")
        elif k=='Condition':
            c=fw['Rule.ConditionHasConditionExpression'].get(st,[None])[0]; p=fw['Rule.ConditionHasPositiveExpression'].get(st,[None])[0]; n=fw['Rule.ConditionHasNegativeExpression'].get(st,[None])[0]
            out.append(f"if({expr(c,depth+1)}) then {expr(p,depth+1)} else {expr(n,depth+1)}")
    return ''.join(out)
def mapping(m,ind):
    lines=[]
    def part(p,lvl):
        tgt=qn(p); ex=fw['Mapping.MappingPartContainsExpression'].get(p,[])
        tr=T(p)[1].get('transformer')
        e=expr(ex[0]) if ex else ''
        if e or tr not in ('discardValue',None): lines.append(ind+'  '*lvl+f"{tgt} <- {e}" + ('' if tr=='getValue' else f'  [{tr}]'))
        for ch in fw['Mapping.MappingPartHasChildren'].get(p,[]): part(ch,lvl+1)
    children=set(c for v in fw['Mapping.MappingPartHasChildren'].values() for c in v)
    for p in fw['Mapping.MappingContainsMappingParts'].get(m,[]):
        if p not in children: part(p,0)
    return lines
# graph
for scope,objs in list(fw['Workflow.ScopeContainsScopeObjects'].items()):
    st,sd=T(scope); print(f"\n##### {st.split('.')[-1]}: {sd.get('name')}")
    nodes=[o for o in objs if not T(o)[0].endswith('Connector')]
    for n in nodes:
        t,d=T(n); k=t.split('.')[-1]
        if k in('Annotation','DataObject'): continue
        line=f"- [{k}] {d.get('name')}"
        if d.get('documentation'): line+=f"  — {d['documentation'][:100]}"
        op=fw['Workflow.ActivityHasOperation'].get(n)
        if op: line+=f"  => op {nm(op[0])}"
        print(line)
        for be in fw['Workflow.ActivityHasBoundaryEvent'].get(n,[]):
            tr=fw['Workflow.CatchEventHasTrigger'].get(be,[])
            bt=[nm(x) for c in bw['Workflow.SourceOfIncomingConnectors'].get(be,[]) for x in fw['Workflow.TargetOfOutgoingConnectors'].get(c,[])]
            print(f"    boundary: {nm(be)} on {[nm(x) for x in tr]} -> {', '.join(bt)}")
        if '-v' in sys.argv:
            for mm in fw['Workflow.InputMappingOfMappableObject'].get(n,[]): print('    IN:'); print('\n'.join(mapping(mm,'      ')[:25]))
        outs=[c for c in fw['Workflow.SourceOfIncomingConnectors'].get(n,[])]
        for c in bw['Workflow.SourceOfIncomingConnectors'].get(n,[]):
            tgt=fw['Workflow.TargetOfOutgoingConnectors'].get(c,[]) 
            cond=fw['Workflow.ConditionalSequenceConnectorContainsExpression'].get(c,[])
            print(f"    -> {', '.join(nm(x) for x in fw['Workflow.TargetOfOutgoingConnectors'].get(c,[]) ) or '?'}" + (f"   IF {expr(cond[0])}" if cond else '') + (f" ({nm(c)})" if T(c)[1].get('name') else ''))
print('\nbuiltins known:',len(BUILTIN))
