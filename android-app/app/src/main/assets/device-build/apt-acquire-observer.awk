# Transparent APT acquire protocol observer. All protocol bytes are forwarded;
# only complete documented message blocks affect local transfer state.
BEGIN { RS="\n"; ORS="\n"; block="" }
{
 print $0; fflush()
 if($0!="") {block=block $0 "\n";next}
 count=split(block,lines,"\n");block="";code=substr(lines[1],1,3);uri="";filename="";size="null";resume=0;cached=0
 for(i=2;i<=count;i++) {
  p=index(lines[i],": ");if(!p)continue
  key=substr(lines[i],1,p-1);value=substr(lines[i],p+2)
  if(key=="IMS-Hit" && value=="true")cached=1
  if(key=="URI")uri=value
  if(key=="Filename")filename=value
  if(key=="Size" && value ~ /^[0-9]+$/ && length(value)<=15 && value+0>0)size=value
  if(key=="Resume-Point" && value ~ /^[0-9]+$/ && length(value)<=15)resume=value
 }
 if(direction=="input" && code=="600" && uri!="" && filename ~ /^\// && filename !~ /[\t\r\n]/) {
  printf "%s\t%s\n",uri,filename >> requests;close(requests)
 }
 if(direction=="output" && (code=="200" || code=="201" || code=="400") && uri!="") {
  if(filename=="") {
   while((getline row < requests)>0) {split(row,cols,"\t");if(cols[1]==uri)filename=cols[2]}
   close(requests)
  }
  if(filename!="" && filename !~ /[\t\r\n]/) {
   if(code=="200" || active!=uri) {sequence++;started=systime();baseline=resume;active=uri}
   phase=(code=="200"?"downloading":code=="201"?(cached?"cached":"complete"):"error")
   # The APT method's completed Size is actual delivered file size, not a plan.
   printf "%s\t%s\t%s\t%s\t%d\t%.0f\t%.0f\n",filename,size,phase,pid "-" sequence,started,baseline,(code=="201" && size!="null"?size:-1) > state ".tmp"
   close(state ".tmp")
   system("mv -- " state ".tmp " state)
   # Emit every lifecycle boundary immediately, even tiny transfers that finish
   # between timer samples. stdout protocol forwarding has already completed.
   system("/bin/sh " emitter " " state)
   if(code!="200")active=""
  }
 }
}
