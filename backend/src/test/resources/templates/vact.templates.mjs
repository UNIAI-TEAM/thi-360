// V-ACT structure and reference distributions, specification §5.
import {assignGroups} from './groups.mjs';

const values = (codes, counts) => counts.map((value, i) => ({code:codes[i], value}));
const matrix = (grades, levels) => ({
  mode:'SEPARATE', unit:'COUNT', basis:'QUESTION', gradeEnforcement:'REFERENCE', difficultyEnforcement:'REFERENCE',
  byGrade:values(['G10','G11','G12','GRADE_MIXED'], grades), byDifficulty:values(['NB','TH','VD'], levels)
});
const block = (name, questionType, questionCount, templateGroupCode, extra={}) =>
  ({name, questionType, questionCount, templateGroupCode, requiredPointsPerQuestion:10, ...extra});
const set = (name, count, size, group, extra={}) => block(name, 'QUESTION_SET', count*size, group,
  {setCount:count, setSize:size, childAnswerType:'SINGLE_CHOICE', ...extra});
const section = (subjectCode, questionCount, competency, blocks, extra={}) =>
  ({subjectCode, questionCount, knowledgeScope:{nodeIds:[], competencyCodes:[competency]}, blocks, ...extra});
const part = (name, sections) => {
  const questionCount = sections.reduce((sum, section) => sum + section.questionCount, 0);
  return {name, questionCount, questionCountOnPaper:questionCount, questionCountRequired:questionCount,
    participation:{mode:'ALL'}, maxPoints:questionCount*10, sections, options:[]};
};

const strands = [
  ['Chính tả, từ vựng và ngữ nghĩa', [5,2,0]],
  ['Thể loại, thi pháp và lí luận văn học', [1,7,4]],
  ['Ngữ pháp, liên kết và logic diễn đạt', [0,3,0]],
  ['Đọc hiểu văn bản, nội dung và lập luận', [0,5,3]]
];
const topicQuotas = strands.flatMap(([name, counts], strand) => counts.flatMap((count, level) => count ? [{
  id:`vact-vietnamese-${strand+1}-${['nb','th','vd'][level]}`, name, nodeIds:[], count,
  basis:'QUESTION', enforcement:'REFERENCE', difficultyCodes:[['NB','TH','VD'][level]]
}] : []));
const vietnamese = section('VIETNAMESE', 30, 'VACT-NN-TV', [
  block('Tiếng Việt', 'SINGLE_CHOICE', 30, 'VACT_VN')
], {matrix:matrix([], [6,17,7]), topicQuotas});
const english = section('ANH', 30, 'VACT-NN-TA', [
  block('Điền khuyết', 'SINGLE_CHOICE', 5, 'VACT_ANH_CLOZE', {tagCodes:['ANH_HOAN_THANH_CAU'], matrix:matrix([], [3,2,0])}),
  block('Phát hiện lỗi sai', 'SINGLE_CHOICE', 5, 'VACT_ANH_ERROR', {tagCodes:['ANH_PHAT_HIEN_LOI'], matrix:matrix([], [0,5,0])}),
  block('Diễn đạt tương đương', 'SINGLE_CHOICE', 5, 'VACT_ANH_EQUIVALENT', {tagCodes:['ANH_DIEN_DAT_TUONG_DUONG'], matrix:matrix([], [0,1,4])}),
  set('Đọc hiểu – bài đọc 1', 1, 7, 'VACT_ANH_READING_1', {tagCodes:['ANH_DOC_HIEU'], matrix:matrix([], [1,4,2])}),
  set('Đọc hiểu – bài đọc 2', 1, 8, 'VACT_ANH_READING_2', {tagCodes:['ANH_DOC_HIEU'], matrix:matrix([], [2,4,2])})
]);
const math = section('TOAN', 30, 'VACT-TOAN', [
  block('Toán học', 'SINGLE_CHOICE', 12, 'VACT_MATH_SINGLE'),
  block('Chùm Toán học', 'QUESTION_SET', 18, 'VACT_MATH_SET', {childAnswerType:'SINGLE_CHOICE'})
], {matrix:matrix([12,9,8,1], [4,10,16])});
const logic = section('SCIENTIFIC_THINKING', 12, 'VACT-TD-LOGIC', [
  set('Logic, phân tích số liệu', 4, 3, 'VACT_LOGIC')
], {matrix:matrix([], [1,4,7])});
const reasoning = section('SCIENTIFIC_THINKING', 18, 'VACT-TD-SL', [
  set('Suy luận khoa học', 6, 3, 'VACT_SCIENCE')
], {matrix:matrix([], [4,9,5])});

const languagePart = part('Sử dụng ngôn ngữ', [vietnamese, english]);
const mathPart = part('Toán học', [math]);
const thinkingPart = part('Tư duy khoa học', [logic, reasoning]);
const source = [];
function add(code, scope, partCode, subjectCode, parts, questions, {full=false}={}) {
  const maxScore = questions*10;
  const template = {code, kindCode:'VACT', scope, partCode, subjectCode, structureYear:null, matrixYear:null,
    timing:'SHARED', durationMinutes:full?150:null, maxScore, fixedCounts:full,
    expertNote:full?'Điểm luyện tập tạm tính 10 điểm mỗi câu; kỳ thi thật dùng IRT.':null,
    label:full?'V-ACT – Đầy đủ cấu trúc':`V-ACT – ${parts[0].name}`, summary:`${questions} câu, thang ${maxScore} điểm.`,
    buildMode:'STANDARD', isDefault:true, existing:full, expected:{questions, maxPoints:maxScore},
    structure:{parts:structuredClone(parts)}};
  assignGroups(template, ({block}) => block.templateGroupCode);
  source.push(template);
}
add('VACT_FULL', 'FULL', null, null, [languagePart, mathPart, thinkingPart], 120, {full:true});
add('VACT_PART_LANGUAGE', 'PART', 'LANGUAGE', null, [languagePart], 60);
add('VACT_PART_THINKING', 'PART', 'THINKING', null, [thinkingPart], 30);
add('VACT_PART_LOGIC', 'PART', 'LOGIC', null, [part('Logic và phân tích số liệu', [logic])], 12);
add('VACT_PART_REASONING', 'PART', 'REASONING', null, [part('Suy luận khoa học', [reasoning])], 18);
add('VACT_SUBJECT_VIETNAMESE', 'SUBJECT', null, 'VIETNAMESE', [part('Tiếng Việt', [vietnamese])], 30);
add('VACT_SUBJECT_ANH', 'SUBJECT', null, 'ANH', [part('Tiếng Anh', [english])], 30);
add('VACT_SUBJECT_TOAN', 'SUBJECT', null, 'TOAN', [mathPart], 30);
source.push({code:'VACT_REVIEW', kindCode:'VACT', scope:'SUBJECT', partCode:null, subjectCode:null,
  structureYear:null, matrixYear:null, timing:'SHARED', durationMinutes:null, maxScore:null, fixedCounts:false,
  expertNote:null, label:'VACT REVIEW', summary:null, buildMode:'REVIEW', isDefault:true,
  expected:{questions:null, maxPoints:null}, structure:{parts:[{name:'Review', sections:[{
    subjectCode:null, knowledgeScope:{nodeIds:[], competencyCodes:[], everyChildInScope:true}, blocks:[{}]
  }], options:[]}]}});
export default source;
