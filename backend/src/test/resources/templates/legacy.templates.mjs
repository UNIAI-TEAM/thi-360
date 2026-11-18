// Preserve the older SP2E assumption while assigning stable IDs and labels.
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
const source=[];
// Keep the already-seeded 2025 assumption as a non-default alternative; it is not the 40-question 2026 structure.
const math=legacyStructure('SP2E_MATH_2025');
const blocks=math.parts[0].sections[0].blocks;
math.parts=blocks.map((block,i)=>({name:['Phần I · Trắc nghiệm','Phần II · Đúng/Sai','Phần III · Trả lời ngắn'][i],questionCount:block.questionCount,questionCountOnPaper:block.questionCount,questionCountRequired:block.questionCount,participation:{mode:'ALL'},maxPoints:[3,4,3][i],sections:[{subjectCode:'TOAN',questionCount:block.questionCount,blocks:[block]}],options:[]}));
source.push({code:'SP2E_MATH_2025',kindCode:'SP2E',scope:'SUBJECT',partCode:null,subjectCode:'TOAN',structureYear:2025,matrixYear:2025,timing:'SHARED',durationMinutes:90,maxScore:10,expertNote:'Cấu trúc 12–4–6 và điểm là giả định, chờ chuyên gia xác nhận.',label:'SP2E – Toán – Cấu trúc giả định 2025',summary:'22 câu trong 90 phút: trắc nghiệm, đúng/sai và trả lời ngắn.',buildMode:'STANDARD',isDefault:false,existing:true,expected:{questions:22,maxPoints:10},structure:math});
// Blocks preserved from the earlier seeds had templateGroupCode null; every
// block now gets a stable <KIND>_<SUBJECT>_<GROUP> code.
const legacyGroup={'Trắc nghiệm chọn một':'SINGLE','Trắc nghiệm':'SINGLE','Đúng/Sai':'TF','Đúng/Sai chung':'TF_COMMON','Đúng/Sai Khoa học máy tính':'TF_CS','Đúng/Sai Tin học ứng dụng':'TF_ICT','Trả lời ngắn':'SHORT','Đọc hiểu':'READING','Viết đoạn văn (khoảng 200 chữ)':'PARA','Viết bài văn (khoảng 600 chữ)':'ESSAY'};
for (const template of source) {
  assignGroups(template, ({block,subjectCode}) => {
    const suffix=legacyGroup[block.name];
    if(!suffix) return null;
    return `${template.kindCode}_${subjectCode}_${suffix}`;
  });
}
export default source;
