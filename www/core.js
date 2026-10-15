(function(root){
const MAXC=24000;
function buildPrompt(text,n){
 const t=text.slice(0,MAXC);
 return [{role:"system",content:"Bạn là giáo viên ra đề. Chỉ dùng nội dung tài liệu được đưa. Chỉ trả về MỘT JSON hợp lệ."},
 {role:"user",content:`Tài liệu:\n"""\n${t}\n"""\n\nRa đúng ${n} câu hỏi kiểm tra bằng tiếng Việt từ tài liệu trên: khoảng 70% trắc nghiệm (4 đáp án, đúng 1) và 30% đúng/sai. Chỉ hỏi điều tài liệu thực sự nói. Mỗi câu kèm "evidence": một câu ngắn trích NGUYÊN VĂN từ tài liệu làm căn cứ.\nĐịnh dạng:\n{"questions":[{"type":"mcq","q":"...","options":["A","B","C","D"],"answer":0,"explain":"...","evidence":"..."},{"type":"tf","q":"...","answer":true,"explain":"...","evidence":"..."}]}\nVới mcq, "answer" là chỉ số 0-3. Với tf, "answer" là true hoặc false.`}];
}
const norm=s=>String(s).replace(/\s+/g," ").trim();
function parseQuestions(raw,source){
 let d;try{d=JSON.parse(String(raw).replace(/^\s*```(json)?|```\s*$/g,"").trim());}catch(e){throw new Error("AI trả về dữ liệu không đọc được");}
 const out=[];
 for(const x of (d&&d.questions)||[]){
  if(!x||typeof x.q!=="string"||!x.q.trim())continue;
  if(x.type==="mcq"){
   if(!Array.isArray(x.options)||x.options.length!==4||!Number.isInteger(x.answer)||x.answer<0||x.answer>3)continue;
   if(new Set(x.options.map(norm)).size!==4)continue;
  }else if(x.type==="tf"){if(typeof x.answer!=="boolean")continue;}
  else continue;
  const ev=norm(x.evidence||"");
  out.push({type:x.type,q:x.q.trim(),options:x.options,answer:x.answer,explain:String(x.explain||""),evidence:ev,grounded:ev.length>0&&(!source||norm(source).includes(ev))});
 }
 return out;
}
function shuffle(a,rng){a=a.slice();const r=rng||Math.random;for(let i=a.length-1;i>0;i--){const j=Math.floor(r()*(i+1));[a[i],a[j]]=[a[j],a[i]];}return a;}
function shuffleExam(qs,rng){return shuffle(qs,rng).map(q=>{if(q.type!=="mcq")return q;const idx=shuffle([0,1,2,3],rng);return Object.assign({},q,{options:idx.map(i=>q.options[i]),answer:idx.indexOf(q.answer)});});}
function grade(qs,ans){let s=0;const items=qs.map((q,i)=>{const ok=ans[i]!==undefined&&ans[i]===q.answer;if(ok)s++;return{ok,given:ans[i]};});return{score:s,total:qs.length,items};}
const api={buildPrompt,parseQuestions,shuffleExam,grade,MAXC};
if(typeof module!=="undefined")module.exports=api;else root.Core=api;
})(this);
