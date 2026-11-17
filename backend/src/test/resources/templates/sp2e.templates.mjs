// SP2E: 2026 structure and the older illustration, requirement table §2.4.
const names={TOAN:'Toán',VAN:'Ngữ văn',LY:'Vật lí',HOA:'Hóa học',SINH:'Sinh học',SU:'Lịch sử',DIA:'Địa lí',ANH:'Tiếng Anh',ECON_LAW:'Giáo dục kinh tế và pháp luật'};
const source=[];
const b=(name,questionType,questionCount,points,extra={})=>({name,questionType,questionCount,requiredPointsPerQuestion:points,...extra});
const set=(name,n,size,points,extra={})=>b(name,'QUESTION_SET',n*size,points,{setCount:n,setSize:size,childAnswerType:'SINGLE_CHOICE',...extra});
const tf=(n,points=1,extra={})=>b('Đúng/Sai','TRUE_FALSE',n,points,{itemShape:{statements:4},...extra});
const part=(name,code,blocks,maxPoints)=>{
  const questionCount=blocks.reduce((n,x)=>n+(x.omitted?0:x.questionCount),0);
  return {name,questionCount,questionCountOnPaper:questionCount,questionCountRequired:questionCount,participation:{mode:'ALL'},maxPoints:maxPoints||null,sections:[{subjectCode:code,questionCount,blocks}],options:[]};
};
const quota=(s)=>({id:`${s.toLowerCase()}-difficulty-reference`,name:'Tỷ trọng độ khó tham khảo',dimension:'DIFFICULTY',basis:'QUESTION',enforcement:'REFERENCE',subjectCodes:[s],buckets:(s==='TOAN'?[30,30,25,15]:s==='VAN'?[20,30,30,20]:[30,30,20,20]).map((percent,i)=>({codes:[['NB','TH','VD','VDC'][i]],percent}))});
const referenceMatrix={mode:'SEPARATE',unit:'COUNT',basis:'QUESTION',gradeEnforcement:'REFERENCE',difficultyEnforcement:'REFERENCE',byGrade:[],byDifficulty:[]};
const note2026='Điểm từng dạng là số tạm; chờ chuyên gia xác nhận.';
const oldNote={LY:'Chờ chuyên gia xác nhận. Phân bố tham khảo theo 50 lệnh hỏi: lớp 10/11/12 = 0/10/40; NB/TH/VD/VDC = 15/15/14/6.',HOA:'Chờ chuyên gia xác nhận. Phân bố tham khảo theo 50 lệnh hỏi: lớp 10/11/12/tổng hợp = 4/5/19/22; NB/TH/VD = 25/16/9.',SINH:'Chờ chuyên gia xác nhận. Phân bố tham khảo theo 20 câu: lớp 10/11/12 = 2/4/14; NB/TH/VD = 4/9/7.',SU:'Chờ chuyên gia xác nhận.'};
function add(code,subject,parts,questions,points,duration,{old=false,note=note2026,existing=false,isDefault=true,rawPoints=points}={}) {
  if(['ANH','SU','DIA','ECON_LAW'].includes(subject)) for(const p of parts) for(const s of p.sections??[]) s.matrix=referenceMatrix;
  source.push({code,kindCode:'SP2E',scope:'SUBJECT',partCode:null,subjectCode:subject,structureYear:old?2025:2026,matrixYear:old?2025:2026,timing:'SHARED',durationMinutes:duration,maxScore:points,expertNote:note,label:old&&subject==='VAN'?'SP2E – Ngữ văn – Đọc hiểu':`SP2E – ${names[subject]} – ${old?'Đề minh họa cũ':'Cấu trúc 2026 theo đề án'}`,summary:`${questions} câu, thang ${points} điểm${duration?`, ${duration} phút`:''}.`,buildMode:'STANDARD',isDefault,existing,expected:{questions,maxPoints:rawPoints},structure:{parts,examQuotas:[quota(subject)]}});
}
const math=[part('Phần I · Lựa chọn','TOAN',[b('Một đáp án','SINGLE_CHOICE',20,.25),b('Nhiều đáp án','MULTIPLE_CHOICE',5,.25)],6.25),part('Phần II · Dữ liệu tổng hợp','TOAN',[b('Dữ liệu tổng hợp',null,5,.25,{tagCodes:['TOAN_DU_LIEU_TONG_HOP']})],1.25),part('Phần III · Trả lời ngắn','TOAN',[b('Trả lời ngắn','SHORT_ANSWER',10,.25)],2.5)];
add('SP2E_MATH_2026','TOAN',math,40,10,90,{existing:true});
const literature2026=[part('Phần I · Đọc hiểu','VAN',[b('Đọc hiểu','SINGLE_CHOICE',20,.25)],5),part('Phần II · Viết đoạn','VAN',[b('Viết đoạn','ESSAY',1,null,{omitted:true})],0),part('Phần III · Viết bài','VAN',[b('Viết bài','ESSAY',1,null,{omitted:true})],0)];
add('SP2E_VAN_2026','VAN',literature2026,20,5,90,{existing:true,isDefault:false,note:'Chờ chuyên gia xác nhận cấu trúc và điểm Đọc hiểu 2026.'});
const textTags=['VAN_THONG_TIN','VAN_THO','VAN_TRUYEN','VAN_VAN_XUOI'];
const literatureOld=[part('Phần I · Đọc hiểu','VAN',[8,8,7,7].map((n,i)=>set(`Văn bản ${i+1}`,1,n,.2,{slots:[{index:1,tagCodes:[textTags[i]]}]})),6),part('Phần II · Viết','VAN',[b('Viết bài','ESSAY',1,4,{omitted:true})],0)];
add('SP2E_VAN_OLD','VAN',literatureOld,30,6,null,{old:true,note:'Chờ chuyên gia xác nhận số câu của từng văn bản.'});
for(const s of ['LY','HOA','SINH','DIA']) {
  const p2=s==='DIA'?[tf(3),tf(1,1,{tagCodes:['DIA_CAU_KY_NANG']})]:[tf(4)];
  const parts=[part('Phần I · Trắc nghiệm',s,[b('Một đáp án','SINGLE_CHOICE',16,.25)],4),part('Phần II · Đúng/Sai',s,p2,4),part('Phần III · Trả lời ngắn',s,[b('Trả lời ngắn','SHORT_ANSWER',8,.25)],2)];
  add(`SP2E_${s}_2026`,s,parts,28,10,60,{existing:true});
}
for(const s of ['SU','ECON_LAW']) {
  const p1=s==='ECON_LAW'?[b('Trắc nghiệm độc lập','SINGLE_CHOICE',20,.25),set('Chùm tình huống',2,3,.25)]:[b('Một đáp án','SINGLE_CHOICE',26,.25)];
  add(`SP2E_${s}_2026`,s,[part('Phần I · Trắc nghiệm',s,p1,6.5),part('Phần II · Đúng/Sai',s,[tf(6)],6)],32,10,60,{existing:true,rawPoints:12.5,note:'Điểm từng câu là tạm; chờ chuyên gia xác nhận.'});
}
const bands=[['Đọc điền câu',10,'ANH_DOC_DIEN_CAU',true],['Đồng nghĩa',4,'ANH_DONG_NGHIA'],['Kết hợp câu',4,'ANH_KET_HOP_CAU'],['Điền từ',5,'ANH_DIEN_TU',true],['Điền cụm hoặc câu',5,'ANH_DIEN_CUM_CAU',true],['Giao tiếp',4,'ANH_GIAO_TIEP'],['Sắp xếp',5,'ANH_SAP_XEP_DOAN'],['Đọc hiểu 1',5,'ANH_DOC_HIEU_1',true],['Đọc hiểu 2',8,'ANH_DOC_HIEU_2',true]];
const eng=part('Phần I · Tiếng Anh','ANH',bands.map(([name,n,tag,cluster])=>cluster?set(name,1,n,.25,{tagCodes:[tag]}):b(name,'SINGLE_CHOICE',n,.25,{tagCodes:[tag]})),12.5);
add('SP2E_ANH_2026','ANH',[eng],50,10,60,{existing:true,rawPoints:12.5});
for(const s of ['LY','HOA','SINH','SU']) {
  const parts=[part('Phần I · Trắc nghiệm',s,[b('Một đáp án','SINGLE_CHOICE',10,.2)],2),part('Phần II · Đúng/Sai',s,[tf(5,.8,{expectedScoring:{trueFalseMethod:'PER_STATEMENT'}})],4),part('Phần III · Ghép đôi',s,[b('Ghép đôi','MATCHING',5,.8,{itemShape:{leftItems:4,rightItems:6}})],4)];
  add(`SP2E_${s}_OLD`,s,parts,20,10,60,{old:true,isDefault:false,note:oldNote[s]});
}
export default source;
