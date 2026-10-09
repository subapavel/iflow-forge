import sys,os,re
sys.path.insert(0,os.path.dirname(__file__))
from moin import load
import xml.etree.ElementTree as ET
from collections import defaultdict
gal,b2=sys.argv[1],sys.argv[2]
el,A=load(gal)
r=lambda x:x.replace('local:','')
fw=defaultdict(lambda: defaultdict(list));bw=defaultdict(lambda: defaultdict(list))
for a,ls in A.items():
    a=a.split('.',1)[1]
    for f,s in ls: fw[a][r(s)].append(r(f)); bw[a][r(f)].append(r(s))
# galaxy edges by MOF id
gedges=set()
for c in set(fw['Workflow.TargetOfOutgoingConnectors'])|set(x for v in bw['Workflow.SourceOfIncomingConnectors'].values() for x in v):
    src=[n for n,cs in bw['Workflow.SourceOfIncomingConnectors'].items() if c in cs]
    tgt=fw['Workflow.TargetOfOutgoingConnectors'].get(c,[])
    for s in src:
        for t in tgt: gedges.add((s,t))
# bpmn2
N='{http://www.omg.org/spec/BPMN/20100524/MODEL}'; X='{http://www.sap.com/bpm/bpmn2}'
root=ET.parse(b2).getroot()
id2mof={}; id2name={}
for e in root.iter():
    m=e.find(N+'extensionElements/'+X+'BPMelementID')
    if e.get('id') and m is not None: id2mof[e.get('id')]=m.text; id2name[e.get('id')]=e.get('name')
bedges=set(); conds={}
for sf in root.iter(N+'sequenceFlow'):
    s,t=id2mof.get(sf.get('sourceRef')),id2mof.get(sf.get('targetRef'))
    bedges.add((s,t))
    ce=sf.find(N+'conditionExpression')
    if ce is not None and ce.text.strip()!='true': conds[id2mof.get(sf.get('id'))]=re.sub(r'\s+',' ',ce.text)
nm=lambda i:(el.get(i,('?',{}))[1].get('name') or el.get(i,('?',))[0].split('.')[-1]) if i else None
print('galaxy edges',len(gedges),'bpmn2 edges',len(bedges),'common',len(gedges&bedges))
for e in sorted(bedges-gedges, key=str): print('  only bpmn2:',nm(e[0]),'->',nm(e[1]), e)
for e in sorted(gedges-bedges, key=str): print('  only galaxy:',nm(e[0]),'->',nm(e[1]))
# function id table: connector expression -> function ids in order
def fids(e,out):
    for st in fw['Rule.ExpressionHasSteps'].get(e,[]):
        k=el.get(st,('?',))[0]
        if k.endswith('FunctionInvocation'):
            out.append(fw['Rule.FunctionInvocationHasFunctionInterface'].get(st,[None])[0])
            for mp in fw['Rule.FunctionInvocationHasParameterMapping'].get(st,[]):
                for p in fw['Mapping.MappingContainsMappingParts'].get(mp,[]):
                    for ex in fw['Mapping.MappingPartContainsExpression'].get(p,[]): fids(ex,out)
    return out
print('\nCONDITIONS')
for c,txt in conds.items():
    ex=fw['Workflow.ConditionalSequenceConnectorContainsExpression'].get(c,[])
    ids=fids(ex[0],[]) if ex else []
    names=re.findall(r'([A-Za-z][\w-]*)\s*\(',txt)
    print(' ',txt[:160]); print('    ids:',[i[:8] if i else None for i in ids],' names:',names)

# contract artificial joins
art={i for i,(t,d) in el.items() if (d.get('name') or '').startswith('Artificial Uncontrolled Join')}
ge=set()
for s,t in gedges:
    if s in art: continue
    stack=[t]; seen=set()
    while stack:
        x=stack.pop()
        if x in art:
            for (a,b) in gedges:
                if a==x and b not in seen: seen.add(b); stack.append(b)
        else: ge.add((s,x))
print('\nAFTER CONTRACTION: galaxy',len(ge),'bpmn2',len(bedges),'diff',len(ge^bedges))
for e in ge^bedges: print('  diff', nm(e[0]),'->',nm(e[1]), 'in galaxy' if e in ge else 'in bpmn2')
# operator table
import json
tab={}
for c,txt in conds.items():
    ex=fw['Workflow.ConditionalSequenceConnectorContainsExpression'].get(c,[])
    ids=fids(ex[0],[]) if ex else []
    names=re.findall(r'([A-Za-z][\w-]*)\s*\(',txt)
    if len(ids)==len(names):
        for i,n in zip(ids,names): tab.setdefault(i,set()).add(n)
    else: print('  skip (infix):',txt[:60])
for i,n in sorted(tab.items()): print('  ',i,sorted(n))
