// Preserve the existing V-ACT and THPTQG figures while assigning stable IDs and labels.
import {assignGroups} from './groups.mjs';

import {readFileSync} from 'node:fs';
const seed=readFileSync(new URL('../../../main/resources/db/migration/exam/V202611161801__seed_exam_templates.sql',import.meta.url),'utf8').split('\n');
const updates=readFileSync(new URL('../../../main/resources/db/migration/exam/V202611171200__assumed_templates_fill.sql',import.meta.url),'utf8').split('\n');
function legacyStructure(code) {
  const row=updates.find(line=>line.includes(`WHERE code = '${code}'`)) ?? seed.find(line=>line.startsWith(`  ('${code}'`));
  const match=row?.match(/'(\{.*\})'::jsonb/);
  if(!match) throw new Error(`Missing existing structure for ${code}`);
  const structure=JSON.parse(match[1].replaceAll("''", "'"));
  // Preserve participation.mode and matrix.mode; only old ExamPart.mode is obsolete.
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
const entries=[
 ['THPTQG_MATH_2025','TOAN','Toán',22,90],['THPTQG_LITERATURE_2025','VAN','Ngữ văn',7,120],
 ['THPTQG_ENGLISH_2025','ANH','Tiếng Anh',40,50],['THPTQG_PHYSICS_2025','LY','Vật lí',28,50],
 ['THPTQG_CHEMISTRY_2025','HOA','Hóa học',28,50],['THPTQG_BIOLOGY_2025','SINH','Sinh học',28,50],
 ['THPTQG_GEOGRAPHY_2025','DIA','Địa lí',28,50],['THPTQG_HISTORY_2025','SU','Lịch sử',28,50],
 ['THPTQG_ECON_LAW_2025','ECON_LAW','Giáo dục kinh tế và pháp luật',28,50],
 ['THPTQG_TECH_INDUSTRY_2025','TECH_INDUSTRY','Công nghệ công nghiệp',28,50],
 ['THPTQG_TECH_AGRICULTURE_2025','TECH_AGRICULTURE','Công nghệ nông nghiệp',28,50],
 ['THPTQG_INFORMATICS_2025','INFORMATICS','Tin học',28,50]
];
const source=entries.map(([code,subject,name,questions,duration])=>({
 code,kindCode:'THPTQG',scope:'SUBJECT',partCode:null,subjectCode:subject,structureYear:2025,matrixYear:2025,
 timing:subject==='INFORMATICS'?'SHARED':'PER_PART',durationMinutes:duration,maxScore:10,
 expertNote:subject==='VAN'?'Đọc hiểu là một chùm 5 câu tự luận, tổng 4 điểm; điểm từng câu theo hướng dẫn chấm.':null,
 label:`THPTQG – ${name} – Cấu trúc 2025`,summary:`${questions} câu, ${duration} phút, thang 10 điểm.`,
 buildMode:'STANDARD',isDefault:true,existing:true,expected:{questions,maxPoints:10},structure:legacyStructure(code)
}));
source.push({code:'VACT_FULL',kindCode:'VACT',scope:'FULL',partCode:null,subjectCode:null,structureYear:null,matrixYear:null,timing:'SHARED',durationMinutes:150,maxScore:1200,expertNote:'Điểm luyện tập tạm tính 10 điểm mỗi câu; kỳ thi thật dùng IRT.',label:'V-ACT – Đầy đủ cấu trúc',summary:'120 câu trong 150 phút, thang 1.200 điểm.',buildMode:'STANDARD',isDefault:true,existing:true,expected:{questions:120,maxPoints:1200},structure:legacyStructure('VACT_FULL')});
// Keep the already-seeded 2025 assumption as a non-default alternative; it is not the 40-question 2026 structure.
const math=legacyStructure('SP2E_MATH_2025');
const blocks=math.parts[0].sections[0].blocks;
math.parts=blocks.map((block,i)=>({name:['Phần I · Trắc nghiệm','Phần II · Đúng/Sai','Phần III · Trả lời ngắn'][i],questionCount:block.questionCount,questionCountOnPaper:block.questionCount,questionCountRequired:block.questionCount,participation:{mode:'ALL'},maxPoints:[3,4,3][i],sections:[{subjectCode:'TOAN',questionCount:block.questionCount,blocks:[block]}],options:[]}));
source.push({code:'SP2E_MATH_2025',kindCode:'SP2E',scope:'SUBJECT',partCode:null,subjectCode:'TOAN',structureYear:2025,matrixYear:2025,timing:'SHARED',durationMinutes:90,maxScore:10,expertNote:'Cấu trúc 12–4–6 và điểm là giả định, chờ chuyên gia xác nhận.',label:'SP2E – Toán – Cấu trúc giả định 2025',summary:'22 câu trong 90 phút: trắc nghiệm, đúng/sai và trả lời ngắn.',buildMode:'STANDARD',isDefault:false,existing:true,expected:{questions:22,maxPoints:10},structure:math});
// Blocks preserved from the earlier seeds had templateGroupCode null; every
// block now gets a stable <KIND>_<SUBJECT>_<GROUP> code.
const legacyGroup={'Trắc nghiệm chọn một':'SINGLE','Trắc nghiệm':'SINGLE','Đúng/Sai':'TF','Đúng/Sai chung':'TF_COMMON','Đúng/Sai Khoa học máy tính':'TF_CS','Đúng/Sai Tin học ứng dụng':'TF_ICT','Trả lời ngắn':'SHORT','Đọc hiểu':'READING','Viết đoạn văn (khoảng 200 chữ)':'PARA','Viết bài văn (khoảng 600 chữ)':'ESSAY','Tiếng Việt':'VN','Tiếng Anh':'EN','Toán học':'MATH','Logic, phân tích số liệu':'LOGIC','Suy luận khoa học':'SCIENCE'};
for (const template of source) {
  assignGroups(template, ({block,subjectCode}) => {
    const suffix=legacyGroup[block.name];
    if(!suffix) return null;
    return template.kindCode==='VACT'?`VACT_${suffix}`:`${template.kindCode}_${subjectCode}_${suffix}`;
  });
}
export default source;
