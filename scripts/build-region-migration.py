"""Generate a reviewable Flyway seed from KMA's official workbook and AirKorea metadata.
Usage: python3 scripts/build-region-migration.py workbook.xlsx stations.json output.sql
Inputs must be official source snapshots. Credentials are never accepted by this script.
"""
import sys,zipfile,xml.etree.ElementTree as ET,json,re,math,uuid,hashlib
from pathlib import Path
workbook,station_file,out=map(Path,sys.argv[1:])
ns={'s':'http://schemas.openxmlformats.org/spreadsheetml/2006/main'}
with zipfile.ZipFile(workbook) as z:
 strings=[''.join(x.itertext()) for x in ET.fromstring(z.read('xl/sharedStrings.xml')).findall('s:si',ns)]
 rows=[]
 for r in ET.fromstring(z.read('xl/worksheets/sheet1.xml')).findall('.//s:row',ns)[1:]:
  row={}
  for c in r.findall('s:c',ns):
   v=c.find('s:v',ns)
   if v is not None:row[re.sub(r'\d','',c.attrib['r'])]=strings[int(v.text)] if c.attrib.get('t')=='s' else v.text
  if row.get('A')=='kor' and row.get('E','').strip():rows.append(row)
assert len(rows)>3000
assert len({r['B'] for r in rows})==len(rows)
stations=json.loads(station_file.read_text())
urban=[]
for s in stations:
 try:
  lat,lon=float(s['dmX']),float(s['dmY'])
  if s['mangName']=='도시대기' and 30<lat<40 and 120<lon<135: urban.append(s)
 except (ValueError,KeyError,TypeError):pass
assert len(urban)>100
assert len({s['stationName'] for s in urban})==len(urban)
def distance(lat,lon,s):
 p1,p2=map(math.radians,[lat,float(s['dmX'])]);dp=p2-p1;dl=math.radians(float(s['dmY'])-lon)
 return 6371*2*math.asin(min(1,math.sqrt(math.sin(dp/2)**2+math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2)))
def q(s):return "'"+str(s).replace("'","''")+"'"
both=[s for s in urban if 'PM10' in s['item'].split(', ') and 'PM2.5' in s['item'].split(', ')]
assert both
lines=['-- Generated from KMA 2026-09-30 administrative coordinate workbook and AirKorea station metadata.',
 '-- Workbook SHA256: '+hashlib.sha256(workbook.read_bytes()).hexdigest(),
 '-- Station input SHA256: '+hashlib.sha256(station_file.read_bytes()).hexdigest(),
 'alter table weather_region add column admin_code varchar(10) unique;',
 'alter table weather_region add column latitude double precision;',
 'alter table weather_region add column longitude double precision;',
 'alter table weather_region add column station_distance_km double precision;']
maxdist=0
for r in rows:
 lat,lon=float(r['O']),float(r['N']);assert 30<lat<40 and 120<lon<135
 closest=min(both,key=lambda s:distance(lat,lon,s));dist=distance(lat,lon,closest);maxdist=max(maxdist,dist)
 rid=str(uuid.uuid5(uuid.NAMESPACE_URL,'https://weatherhood.local/administrative-region/'+r['B']))
 name=' '.join(r.get(k,'') for k in ['C','D','E']).replace('  ',' ').strip()
 vals=[q(rid),q(name),'true',r['F'],r['G'],q(closest['stationName']),q(closest['stationName']),q(r['B']),str(lat),str(lon),str(round(dist,3))]
 lines.append('insert into weather_region(id,name,supported,nx,ny,pm10_station,pm25_station,admin_code,latitude,longitude,station_distance_km) values ('+','.join(vals)+');')
out.write_text('\n'.join(lines)+'\n')
print(json.dumps({'regions':len(rows),'urbanStations':len(urban),'bothPmStations':len(both),'maximumDistanceKm':round(maxdist,2),'provinceNames':sorted(set(r['C'] for r in rows))},ensure_ascii=False))
