// SPT V1, requirement table §2.5. Essay groups remain visible but omitted.
const names={TOAN:'Toán',VAN:'Ngữ văn',ANH:'Tiếng Anh',LY:'Vật lí',HOA:'Hóa học',SINH:'Sinh học',SU:'Lịch sử',DIA:'Địa lí',INFORMATICS:'Tin học',ECON_LAW:'Giáo dục kinh tế và pháp luật',TECH_INDUSTRY:'Công nghệ công nghiệp',TECH_AGRICULTURE:'Công nghệ nông nghiệp'};
const numbers={TOAN:[16,2,4,3,7],LY:[12,3,4,2,7],HOA:[20,2,4,1,8],SINH:[12,3,4,3,7],SU:[20,2,0,3,7],DIA:[12,3,4,1,7],ECON_LAW:[16,3,0,2,7],TECH_INDUSTRY:[16,2,4,2,7],TECH_AGRICULTURE:[16,2,4,2,7]};
const source=[];
const b=(name,questionType,questionCount,points,extra={})=>({name,questionType,questionCount,requiredPointsPerQuestion:points,...extra});
const essay=n=>b('Tự luận – tạm bỏ qua','ESSAY',n,null,{omitted:true});
const tf=n=>b('Đúng/Sai','TRUE_FALSE',n,1,{itemShape:{statements:4},expectedScoring:{trueFalseMethod:'COUNT_TABLE',trueFalseTable:[0.1,0.25,0.5,1]}});
const set=(name,n,size,points,extra={})=>b(name,'QUESTION_SET',n*size,points,{setCount:n,setSize:size,childAnswerType:'SINGLE_CHOICE',...extra});
const section=(code,blocks)=>({subjectCode:code,questionCount:blocks.reduce((sum,x)=>sum+(x.omitted?0:x.questionCount),0),blocks});
const part=(name,code,blocks,maxPoints,extra={})=>{
  const s=section(code,blocks), questionCount=s.questionCount;
  return {name,questionCount,questionCountOnPaper:questionCount,questionCountRequired:questionCount,maxPoints,participation:{mode:'ALL'},sections:[s],options:[],...extra};
};
const reference={mode:'SEPARATE',unit:'COUNT',basis:'QUESTION',gradeEnforcement:'REFERENCE',difficultyEnforcement:'REFERENCE',byGrade:[],byDifficulty:[]};
function add(code,subject,parts,questions,points) {
  if(['ANH','SU','DIA','ECON_LAW'].includes(subject)) for(const p of parts) for(const s of p.sections??[]) s.matrix=reference;
  source.push({code,kindCode:'SPT',scope:'SUBJECT',partCode:null,subjectCode:subject,structureYear:2025,matrixYear:2025,timing:'PER_PART',durationMinutes:null,maxScore:points,expertNote:null,label:subject==='VAN'?'SPT – Ngữ văn – Đọc hiểu':`SPT – ${names[subject]} – V1 theo đề tham khảo 075`,summary:`${questions} câu, thang ${points} điểm; phần tự luận tạm bỏ qua.`,buildMode:'STANDARD',isDefault:true,existing:true,expected:{questions,maxPoints:points},structure:{parts,examQuotas:[]}});
}
for(const [s,[mc,t,f,e,points]] of Object.entries(numbers)) {
  const parts=[part('Phần I · Trắc nghiệm',s,[b('Một đáp án','SINGLE_CHOICE',mc,.25)],mc*.25),part('Phần II · Đúng/Sai',s,[tf(t)],t)];
  if(f) parts.push(part('Phần III · Trả lời ngắn',s,[b('Trả lời ngắn','SHORT_ANSWER',f,.25)],f*.25));
  parts.at(-1).sections[0].blocks.push(essay(e));
  add(s==='TOAN'?'SPT_MATH_2025':`SPT_${s}_2025`,s,parts,mc+t+f,points);
}
const van=[part('Phần I · Đọc hiểu','VAN',[set('Hai văn bản',2,5,.3,{slots:[{index:1,tagCodes:['VAN_KHOA_HOC']},{index:2,tagCodes:['VAN_THONG_TIN']}]})],3),part('Phần II · Viết','VAN',[b('Nghị luận xã hội','ESSAY',1,null,{omitted:true}),b('Nghị luận văn học','ESSAY',1,null,{omitted:true})],null)];
add('SPT_VAN_2025','VAN',van,10,3);
const anh=[part('Phần I · Use of English','ANH',[b('Câu đơn','SINGLE_CHOICE',5,.25,{tagCodes:['ANH_TU_VUNG_NGU_PHAP']}),set('Đọc điền',2,6,.25,{tagCodes:['ANH_DOC_DIEN']}),b('Sắp xếp hội thoại','SINGLE_CHOICE',1,.25,{tagCodes:['ANH_SAP_XEP_HOI_THOAI']})],4.5),part('Phần II · Reading','ANH',[set('Đọc hiểu',2,5,.25,{tagCodes:['ANH_DOC_HIEU']})],2.5),part('Phần III · Word Formation','ANH',[b('Word Formation','CLOZE',4,.25,{setCount:1,setSize:4,tagCodes:['ANH_WORD_FORMATION']}),essay(1)],1)];
add('SPT_ANH_2025','ANH',anh,32,8);
const informatics=[part('Phần I · Trắc nghiệm','INFORMATICS',[b('Một đáp án','SINGLE_CHOICE',20,.25)],5),part('Phần II · Đúng/Sai','INFORMATICS',[tf(1),essay(3)],2,{questionCount:2,questionCountOnPaper:3,questionCountRequired:2,participation:{mode:'CHOICE',choose:1},options:[{kind:'BRANCH',code:'CS',name:'Khoa học máy tính',sections:[section('INFORMATICS',[tf(1)])]},{kind:'BRANCH',code:'ICT',name:'Tin học ứng dụng',sections:[section('INFORMATICS',[tf(1)])]}]})];
add('SPT_INFORMATICS_2025','INFORMATICS',informatics,22,7);
export default source;
