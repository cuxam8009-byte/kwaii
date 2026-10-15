const assert=require("assert"),C=require("./www/core.js");
const src="Lục lạp là nơi diễn ra quang hợp. Oxy sinh ra từ nước.";
const raw=JSON.stringify({questions:[
{type:"mcq",q:"Quang hợp ở đâu?",options:["Ti thể","Lục lạp","Nhân","Ribôxôm"],answer:1,explain:"x",evidence:"Lục lạp là nơi diễn ra quang hợp."},
{type:"tf",q:"Oxy từ CO2.",answer:false,explain:"y",evidence:"Oxy sinh ra từ nước."},
{type:"mcq",q:"Hỏng",options:["a","a","b","c"],answer:0},
{type:"mcq",q:"Thiếu đáp án",options:["a","b"],answer:0},
{type:"tf",q:"Sai kiểu",answer:"đúng"},{type:"essay",q:"x"},
{type:"tf",q:"Bịa căn cứ",answer:true,evidence:"Câu không có trong tài liệu"}]});
const qs=C.parseQuestions("```json\n"+raw+"\n```",src);
assert.strictEqual(qs.length,3,"chỉ giữ câu hợp lệ");
assert.deepStrictEqual(qs.map(q=>q.grounded),[true,true,false]);
let seed=7;const rng=()=>(seed=(seed*16807)%2147483647)/2147483647;
for(let k=0;k<200;k++){const sh=C.shuffleExam(qs,rng);assert.strictEqual(sh.length,3);
 const m=sh.find(q=>q.q==="Quang hợp ở đâu?");assert.strictEqual(m.options[m.answer],"Lục lạp","đáp án đúng đi theo nội dung");}
const g=C.grade(qs,{0:1,1:true});assert.strictEqual(g.score,1);assert.strictEqual(g.total,3);assert.strictEqual(g.items[2].ok,false);
assert.throws(()=>C.parseQuestions("không phải json",src));
assert.ok(C.buildPrompt("a".repeat(50000),10)[1].content.length<C.MAXC+1500);
console.log("OK: tất cả test logic đều qua");
