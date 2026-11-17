// Requirement table §2.1–2.3. Counts represent the questions a candidate takes.
const subjectNames = {TOAN:'Toán', VAN:'Ngữ văn', ANH:'Tiếng Anh', LY:'Vật lí', HOA:'Hóa học', SINH:'Sinh học', SU:'Lịch sử', DIA:'Địa lí'};
const scientific = ['LY','HOA','SINH','SU','DIA'];
const scienceMatrix = {
  HSA: {LY:[[5,6,6],[3,1,13]], HOA:[[2,8,7],[4,5,8]], SINH:[[3,5,9],[7,7,3]], SU:[[2,5,10],[7,8,2]], DIA:[[2,4,11],[5,9,3]]},
  QDA: {LY:[[6,5,14],[]], HOA:[[6,5,14],[4,20,1]], SINH:[[3,6,16],[]], SU:[[2,6,17],[8,10,7]], DIA:[[4,4,17],[]]},
  BCA: {LY:[[2,3,10],[3,4,7,1]], HOA:[[3,3,9],[1,7,6,1]], SINH:[[2,3,10],[4,5,4,2]], DIA:[[2,1,12],[2,10,3,0]]}
};
const vals = (codes, values) => values.map((value, i) => ({code:codes[i], value}));
const matrix = (grades, levels, gradeEnforcement='REQUIRED', difficultyEnforcement='REQUIRED', unit='COUNT') => ({
  mode:'SEPARATE', unit, basis:'QUESTION', gradeEnforcement, difficultyEnforcement,
  byGrade:vals(['G10','G11','G12'], grades), byDifficulty:vals(['NB','TH','VD','VDC'], levels)
});
const block = (name, questionType, questionCount, extra={}) => ({name, questionType, questionCount, requiredPointsPerQuestion:1, ...extra});
const set = (name, count, size, extra={}) => block(name,'QUESTION_SET',count*size,{setCount:count,setSize:size,childAnswerType:'SINGLE_CHOICE',...extra});
const section = (subjectCode, questionCount, blocks, extra={}) => ({subjectCode,questionCount,blocks,...extra});
const part = (name, questionCount, durationMinutes, sections, extra={}) => ({
  name, durationMinutes, questionCount, questionCountOnPaper:questionCount, questionCountRequired:questionCount,
  maxPoints:questionCount, participation:{mode:'ALL'}, sections, options:[], ...extra
});
const litTags = ['VAN_TU_KHAC_NHOM','VAN_TU_SAI','VAN_CAP_TU_DIEN','VAN_NGU_LIEU_MOT_CAU'];
function literature(kind) {
  const order = kind === 'HSA' ? [0,1,2,3] : [0,2,1,3];
  const texts = kind === 'HSA'
    ? ['VAN_NGHI_LUAN_XA_HOI','VAN_THONG_TIN','VAN_KHOA_HOC_CHINH_LUAN','VAN_THO','VAN_VAN_XUOI']
    : ['VAN_THONG_TIN_NGHI_LUAN','VAN_NGHI_LUAN','VAN_THO','VAN_THIEU_NHI','VAN_TRUYEN'];
  return section('VAN',50,[...order.map(i=>block(['Từ khác nhóm nghĩa','Từ sai','Cặp từ điền chỗ trống','Một ngữ liệu một câu'][i],'SINGLE_CHOICE',i===3?10:5,{tagCodes:[litTags[i]]})),
    set('Đọc hiểu',5,5,{shuffleUnits:true,shuffleEditable:true,slots:texts.map((tag,i)=>({index:i+1,tagCodes:[tag]}))})]);
}
const englishBands = {
 HSA:[['Hoàn thành câu',10,'ANH_HOAN_THANH_CAU'],['Đồng nghĩa',2,'ANH_DONG_NGHIA'],['Trái nghĩa',2,'ANH_TRAI_NGHIA'],['Viết lại câu',4,'ANH_VIET_LAI_CAU'],['Kết hợp câu',4,'ANH_KET_HOP_CAU'],['Hội thoại',4,'ANH_HOI_THOAI'],['Sắp xếp hội thoại',4,'ANH_SAP_XEP_HOI_THOAI'],['Đọc điền',5,'ANH_DOC_DIEN',true],['Đọc hiểu 1',5,'ANH_DOC_HIEU_1',true],['Đọc hiểu 2',5,'ANH_DOC_HIEU_2',true],['Tư duy logic',5,'ANH_TU_DUY_LOGIC']],
 QDA:[['Hoàn thành câu',10,'ANH_HOAN_THANH_CAU'],['Đồng nghĩa hoặc trái nghĩa',4,['ANH_DONG_NGHIA','ANH_TRAI_NGHIA']],['Hội thoại',4,'ANH_HOI_THOAI'],['Thông báo',4,'ANH_THONG_BAO'],['Viết lại câu',4,'ANH_VIET_LAI_CAU'],['Kết hợp câu',4,'ANH_KET_HOP_CAU'],['Đọc điền',5,'ANH_DOC_DIEN',true],['Đọc hiểu 1',5,'ANH_DOC_HIEU_1',true],['Đọc hiểu 2',5,'ANH_DOC_HIEU_2',true],['Suy luận',5,'ANH_SUY_LUAN']]
};
function english(kind) {
  const blocks=englishBands[kind].map(([name,n,tag,cluster])=> cluster ? set(name,1,n,{tagCodes:[tag]}) : block(name,'SINGLE_CHOICE',n,{tagCodes:Array.isArray(tag)?tag:[tag]}));
  return section('ANH',50,blocks, kind==='HSA' ? {matrix:matrix([],[],'REFERENCE','REFERENCE')} : {});
}
function science(kind, code) {
  const [grades, levels]=scienceMatrix[kind][code];
  const n=kind==='HSA'?17:kind==='QDA'?25:15;
  const hasShort=kind!=='BCA' && ['LY','HOA','SINH'].includes(code);
  const blocks=[block('Trắc nghiệm độc lập','SINGLE_CHOICE',n-3-(hasShort?1:0)),set('Chùm',1,3)];
  if (hasShort) blocks.push(block('Trả lời ngắn','SHORT_ANSWER',1));
  return section(code,n,blocks,{matrix:matrix(grades,levels,'REQUIRED',kind==='BCA'?'REFERENCE':'REQUIRED'), ...(kind==='HSA'?{choiceGroup:['LY','HOA','SINH'].includes(code)?'NATURAL':'SOCIAL'}:{})});
}
const answerQuotas={unit:'COUNT',items:[{answerType:'SINGLE_CHOICE',value:35},{answerType:'SHORT_ANSWER',value:15}]};
const hsaMath=section('TOAN',50,[block('Trắc nghiệm độc lập','SINGLE_CHOICE',32),block('Trả lời ngắn','SHORT_ANSWER',15),set('Chùm cuối',1,3)],{answerQuotas,matrix:matrix([3,20,27],[2,21,20,7])});
const qdaMath=section('TOAN',50,[block('Trắc nghiệm độc lập','SINGLE_CHOICE',32),set('Chùm trắc nghiệm',1,3),block('Trả lời ngắn','SHORT_ANSWER',15,{answerFormats:['INTEGER','DECIMAL','FRACTION']})],{answerQuotas,matrix:matrix([5,15,30],[])});
const gradeQuota={id:'hsa-english-grade',name:'Lớp Tiếng Anh tham khảo',dimension:'GRADE',basis:'QUESTION',enforcement:'REFERENCE',subjectCodes:['ANH'],buckets:[{codes:['G10','G11'],percent:30},{codes:['G12'],percent:70}]};
const bcaQuotas=[{id:'bca-grade',name:'Phân bố lớp tham khảo',dimension:'GRADE',basis:'QUESTION',enforcement:'REFERENCE',excludeSubjectCodes:['VAN','ANH'],buckets:[{codes:['G10','G11'],count:18},{codes:['G12'],count:42}]},{id:'bca-level',name:'Độ khó tham khảo',dimension:'DIFFICULTY',basis:'QUESTION',enforcement:'REFERENCE',excludeSubjectCodes:['VAN'],buckets:[{codes:['NB','TH'],count:24},{codes:['VD'],count:40},{codes:['VDC'],count:16}]}];
const bcaMath=section('TOAN',35,[block('Trắc nghiệm độc lập','SINGLE_CHOICE',27),set('Chùm trắc nghiệm',1,3),block('Trả lời ngắn','SHORT_ANSWER',5)],{matrix:matrix([4,7,24],[2,12,15,6],'REQUIRED','REFERENCE')});
const bcaHistory=section('SU',10,[block('Trắc nghiệm độc lập','SINGLE_CHOICE',7),set('Tư liệu lịch sử',1,3,{tagCodes:['SU_TU_LIEU_LICH_SU']})],{matrix:matrix([1,2,7],[1,6,3,0],'REQUIRED','REFERENCE')});
const bcaEnglish=section('ANH',20,[block('Phát âm','SINGLE_CHOICE',1,{tagCodes:['ANH_PHAT_AM'],requiredPointsPerQuestion:.75}),block('Trọng âm','SINGLE_CHOICE',1,{tagCodes:['ANH_TRONG_AM'],requiredPointsPerQuestion:.75}),block('Từ vựng và ngữ pháp','SINGLE_CHOICE',8,{tagCodes:['ANH_TU_VUNG_NGU_PHAP'],requiredPointsPerQuestion:.75}),set('Đọc điền',1,5,{tagCodes:['ANH_DOC_DIEN'],requiredPointsPerQuestion:.75}),set('Đọc hiểu',1,5,{tagCodes:['ANH_DOC_HIEU'],requiredPointsPerQuestion:.75})],{matrix:matrix([],[3,9,8,0],'REQUIRED','REFERENCE')});
const bcaEssay=section('VAN',1,[block('Nghị luận xã hội từ 500 chữ','ESSAY',1,{requiredPointsPerQuestion:25,tagCodes:['VAN_NGHI_LUAN_XA_HOI']})]);
const scienceOption={kind:'SUBJECT_CHOICE',code:'SCIENCE',name:'Khoa học',chooseSubjects:3,countsByPick:[17,17,16],sections:scientific.map(s=>science('HSA',s))};
const hsaChoice=part('Khoa học hoặc Tiếng Anh',50,60,[],{participation:{mode:'CHOICE',choose:1},options:[scienceOption,{kind:'SUBJECT',code:'ENGLISH',name:'Tiếng Anh',sections:[english('HSA')]}]});
const qdaChoice=part('Khoa học hoặc Tiếng Anh',50,60,[],{codeSource:{source:'GLOBAL_LIST',branch:null},subjectPool:[...scientific.map(s=>science('QDA',s)),english('QDA')]});
const bcaChoice=part('Trắc nghiệm tự chọn',15,null,[],{codeSource:{source:'GLOBAL_LIST',branch:null},subjectPool:['LY','HOA','SINH','DIA'].map(s=>science('BCA',s))});
const sources=[];
function add(code,kind,scope,partCode,subjectCode,parts,questions,points,duration,extra={}) {
  const title=scope==='FULL'?'Đầy đủ':scope==='SUBJECT'?subjectNames[subjectCode]:parts[0].name;
  const note=extra.note??null;
  sources.push({code,kindCode:kind,scope,partCode,subjectCode,structureYear:2026,matrixYear:2026,timing:kind==='BCA'?'SHARED':'PER_PART',durationMinutes:duration,maxScore:points,expertNote:note,label:`${kind} – ${title}`,summary:`${questions} câu, thang ${points} điểm${duration ? `, ${duration} phút` : ''}.`,buildMode:'STANDARD',isDefault:true,existing:extra.existing??false,expected:{questions,maxPoints:points},structure:{parts,examQuotas:extra.quotas??[]}});
}
const mathPart=(kind)=>part('Toán học và xử lí số liệu',50,kind==='HSA'?75:80,[kind==='HSA'?hsaMath:qdaMath]);
const litPart=(kind)=>part('Ngôn ngữ – Văn học',50,kind==='HSA'?60:55,[literature(kind)]);
for (const kind of ['HSA','QDA']) {
  const choice=kind==='HSA'?hsaChoice:qdaChoice;
  add(`${kind}_FULL`,kind,'FULL',null,null,[mathPart(kind),litPart(kind),choice],150,150,195,{existing:true,note:kind==='HSA'?'Chờ chuyên gia xác nhận':null,quotas:kind==='HSA'?[gradeQuota]:[]});
  if(kind==='HSA') add('HSA_PART_MATH',kind,'PART','TOAN',null,[mathPart(kind)],50,50,75,{existing:true});
  add(`${kind}_PART_LITERATURE`,kind,'PART','VAN',null,[litPart(kind)],50,50,kind==='HSA'?60:55,{existing:kind==='HSA'});
  const sci=kind==='HSA' ? part('Khoa học',50,60,[],{participation:{mode:'CHOICE',choose:1},options:[scienceOption]}) : part('Khoa học',50,60,[],{codeSource:{source:'GLOBAL_LIST',branch:'SCIENCE'},subjectPool:scientific.map(s=>science(kind,s))});
  const eng=kind==='HSA' ? part('Tiếng Anh',50,60,[english(kind)]) : part('Tiếng Anh',50,60,[],{codeSource:{source:'GLOBAL_LIST',branch:'ENGLISH'},subjectPool:[english(kind)]});
  add(`${kind}_PART_SCIENCE`,kind,'PART','SCIENCE',null,[sci],50,50,60,{existing:kind==='HSA'});
  add(`${kind}_PART_ENGLISH`,kind,'PART','ANH',null,[eng],50,50,60,{note:kind==='HSA'?'Chờ chuyên gia xác nhận':null,quotas:kind==='HSA'?[gradeQuota]:[]});
  for(const s of ['TOAN','VAN','ANH',...scientific]) {
    const sec=s==='TOAN'?(kind==='HSA'?hsaMath:qdaMath):s==='VAN'?literature(kind):s==='ANH'?english(kind):science(kind,s);
    const n=sec.questionCount;
    add(`${kind}_SUBJECT_${s}`,kind,'SUBJECT',null,s,[part(subjectNames[s],n,null,[sec])],n,n,null,{note:kind==='HSA'&&s==='ANH'?'Chờ chuyên gia xác nhận':null,quotas:kind==='HSA'&&s==='ANH'?[gradeQuota]:[]});
  }
}
const bcaParts=[part('Tự luận Ngữ văn',1,null,[bcaEssay],{maxPoints:25}),part('Trắc nghiệm bắt buộc',65,null,[bcaMath,bcaHistory,bcaEnglish],{maxPoints:60}),bcaChoice];
add('BCA_FULL','BCA','FULL',null,null,bcaParts,81,100,180,{existing:true,quotas:bcaQuotas});
for(const [i,code] of ['VAN_TU_LUAN','TN_BAT_BUOC','TN_TU_CHON'].entries()) add(`BCA_PART_${code}`,'BCA','PART',code,null,[bcaParts[i]],[1,65,15][i],[25,60,15][i],null);
for(const s of ['VAN','TOAN','SU','ANH','LY','HOA','SINH','DIA']) {
  const sec=s==='VAN'?bcaEssay:s==='TOAN'?bcaMath:s==='SU'?bcaHistory:s==='ANH'?bcaEnglish:science('BCA',s);
  const points=s==='VAN'?25:s==='ANH'?15:sec.questionCount;
  add(`BCA_SUBJECT_${s}`,'BCA','SUBJECT',null,s,[part(subjectNames[s],sec.questionCount,null,[sec],{maxPoints:points})],sec.questionCount,points,null);
}
export default sources;
