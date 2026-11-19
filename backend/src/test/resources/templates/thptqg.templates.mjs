// THPTQG subject structures, supplement specification §4.
import {readFileSync} from 'node:fs';
import {assignGroups} from './groups.mjs';

const entries=[
  ['MATH','TOAN','Toán',22,90], ['LITERATURE','VAN','Ngữ văn',7,120],
  ['ENGLISH','ANH','Tiếng Anh',40,50], ['PHYSICS','LY','Vật lí',28,50],
  ['CHEMISTRY','HOA','Hóa học',28,50], ['BIOLOGY','SINH','Sinh học',28,50],
  ['GEOGRAPHY','DIA','Địa lí',28,50], ['HISTORY','SU','Lịch sử',28,50],
  ['ECON_LAW','ECON_LAW','Giáo dục kinh tế và pháp luật',28,50],
  ['TECH_INDUSTRY','TECH_INDUSTRY','Công nghệ công nghiệp',28,50],
  ['TECH_AGRICULTURE','TECH_AGRICULTURE','Công nghệ nông nghiệp',28,50],
  ['INFORMATICS','INFORMATICS','Tin học',28,50]
];
const block=(name,questionType,questionCount,points,extra={})=>({name,questionType,questionCount,requiredPointsPerQuestion:points,...extra});
const set=(name,size,tag)=>block(name,'QUESTION_SET',size,.25,{setCount:1,setSize:size,childAnswerType:'SINGLE_CHOICE',tagCodes:[tag]});
const part=(name,subject,blocks,maxPoints)=>{
  const questionCount=blocks.reduce((sum,item)=>sum+item.questionCount,0);
  return {name,questionCount,questionCountOnPaper:questionCount,questionCountRequired:questionCount,participation:{mode:'ALL'},maxPoints,sections:[{subjectCode:subject,questionCount,blocks}],options:[]};
};

// Literature and informatics keep the exact structure of the earlier seed.
function preservedStructure(code) {
  const seed=readFileSync(new URL('../../../main/resources/db/migration/exam/V202611161801__seed_exam_templates.sql',import.meta.url),'utf8').split('\n');
  const updates=readFileSync(new URL('../../../main/resources/db/migration/exam/V202611171200__assumed_templates_fill.sql',import.meta.url),'utf8').split('\n');
  const row=updates.find(line=>line.includes(`WHERE code = '${code}'`)) ?? seed.find(line=>line.startsWith(`  ('${code}'`));
  const match=row?.match(/'(\{.*\})'::jsonb/);
  if(!match) throw new Error(`Missing existing structure for ${code}`);
  const structure=JSON.parse(match[1].replaceAll("''", "'"));
  function strip(value) {
    if(Array.isArray(value)) return value.forEach(strip);
    if(value && typeof value==='object') {
      delete value.taskTypeCode; delete value.groupOrder; delete value.shuffle;
      if('participation' in value && 'sections' in value) delete value.mode;
      Object.values(value).forEach(strip);
    }
  }
  strip(structure);
  return structure;
}

const groupNames={
  'Trắc nghiệm chọn một':'SINGLE', 'Đúng/Sai':'TF', 'Trả lời ngắn':'SHORT',
  'Đúng/Sai chung':'TF_COMMON', 'Đúng/Sai Khoa học máy tính':'TF_CS', 'Đúng/Sai Tin học ứng dụng':'TF_ICT',
  'Đọc hiểu':'READING', 'Viết đoạn văn (khoảng 200 chữ)':'PARA', 'Viết bài văn (khoảng 600 chữ)':'ESSAY',
  'Reading 1 – Đọc điền':'CLOZE_1', 'Reading 2 – Đọc điền':'CLOZE_2',
  'Sắp xếp hội thoại hoặc văn bản':'ORDER', 'Reading 3 – Đọc điền câu':'GAP_SENT',
  'Reading 4 – Đọc hiểu':'READ_1', 'Reading – Đọc hiểu':'READ_2'
};
const source=entries.map(([suffix,subject,name,questions,duration])=>{
  const code=`THPTQG_${suffix}_2025`;
  let structure, idVersion;
  if(subject==='VAN' || subject==='INFORMATICS') {
    structure=preservedStructure(code);
    if(subject==='VAN') structure.parts[0].sections[0].blocks[0].matrix={
      mode:'SEPARATE',unit:'COUNT',basis:'QUESTION',gradeEnforcement:'REFERENCE',difficultyEnforcement:'REFERENCE',
      byGrade:[],byDifficulty:[{code:'NB',value:2},{code:'TH',value:2},{code:'VD',value:1}]
    };
  } else if(subject==='ANH') {
    const blocks=[set('Reading 1 – Đọc điền',6,'ANH_DOC_DIEN'),set('Reading 2 – Đọc điền',6,'ANH_DOC_DIEN'),
      block('Sắp xếp hội thoại hoặc văn bản','SINGLE_CHOICE',5,.25,{tagCodes:['ANH_SAP_XEP_HOI_THOAI','ANH_SAP_XEP_DOAN']}),
      set('Reading 3 – Đọc điền câu',5,'ANH_DOC_DIEN_CAU'),set('Reading 4 – Đọc hiểu',8,'ANH_DOC_HIEU'),set('Reading – Đọc hiểu',10,'ANH_DOC_HIEU')];
    structure={parts:[{...part('Tiếng Anh',subject,blocks,10),durationMinutes:50}]};
  } else {
    const math=subject==='TOAN', short=math || ['LY','HOA','SINH','DIA'].includes(subject);
    const mc=math?12:short?18:24;
    const parts=[part('Phần I · Trắc nghiệm',subject,[block('Trắc nghiệm chọn một','SINGLE_CHOICE',mc,.25)],mc*.25),
      part('Phần II · Đúng/Sai',subject,[block('Đúng/Sai','TRUE_FALSE',4,1,{itemShape:{statements:4},expectedScoring:{trueFalseMethod:'COUNT_TABLE',trueFalseTable:[0.1,0.25,0.5,1]}})],4)];
    if(short) parts.push(part('Phần III · Trả lời ngắn',subject,[block('Trả lời ngắn','SHORT_ANSWER',6,math?.5:.25)],math?3:1.5));
    structure={parts};
    // The earlier one-part structure must never match these parts by id when a draft is reloaded.
    idVersion='split-parts';
  }
  const template={code,kindCode:'THPTQG',scope:'SUBJECT',partCode:null,subjectCode:subject,structureYear:2025,matrixYear:2025,
    timing:['VAN','ANH'].includes(subject)?'PER_PART':'SHARED',durationMinutes:duration,maxScore:10,
    expertNote:subject==='VAN'?'Đọc hiểu là một chùm 5 câu tự luận, tổng 4 điểm; điểm từng câu theo hướng dẫn chấm.':null,
    label:`THPTQG – ${name} – Cấu trúc 2025`,summary:`${questions} câu, ${duration} phút, thang 10 điểm.`,
    buildMode:'STANDARD',isDefault:true,existing:true,expected:{questions,maxPoints:10},structure,
    ...(subject==='VAN'?{publishable:false}:{}),...(idVersion?{idVersion}:{})};
  assignGroups(template,({block,subjectCode})=>groupNames[block.name]?`THPTQG_${subjectCode}_${groupNames[block.name]}`:null);
  return template;
});
source.push({code:'THPTQG_REVIEW',kindCode:'THPTQG',scope:'SUBJECT',partCode:null,subjectCode:null,structureYear:null,matrixYear:null,
  timing:'PER_PART',durationMinutes:null,maxScore:null,expertNote:null,label:'THPTQG REVIEW',summary:null,buildMode:'REVIEW',isDefault:true,
  expected:{questions:0,maxPoints:0},structure:{parts:[{name:'Review',sections:[{subjectCode:null,blocks:[{}]}],options:[]}]}});
export default source;
