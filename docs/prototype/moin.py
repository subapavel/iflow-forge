import sys,re
from collections import defaultdict,Counter
import xml.etree.ElementTree as ET
M='{http://xml.sap.com/moin}'
def load(path):
    root=ET.parse(path).getroot()
    el={}; assoc=defaultdict(list)
    for e in root:
        tag=e.tag
        if tag in('C','M'): continue
        if M+'i' in e.attrib:
            d=dict((k,v) for k,v in e.attrib.items() if not k.startswith(M))
            for c in e:
                if c.tag in('name','documentation'): d[c.tag]=c.get('originalText')
            el[e.attrib[M+'i']]=(tag,d)
        else:
            for l in e:
                if l.tag=='L': assoc[tag].append((l.get('f'),l.get('s')))
    return el,assoc
if __name__=='__main__':
    el,assoc=load(sys.argv[1])
    print('elements',len(el),'assocs',len(assoc))
    for a,ls in sorted(assoc.items()):
        c=Counter((el.get(f,('?'+f[:6],))[0].split('.')[-1], el.get(s.replace('local:',''),('EXT',))[0].split('.')[-1]) for f,s in ls)
        print(f'{a.split(".",1)[-1]:55s} {len(ls):4d}  '+', '.join(f'{x}->{y}:{n}' for (x,y),n in c.most_common(4)))
