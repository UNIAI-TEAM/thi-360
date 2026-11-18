// V-SAT structures, addendum specification 2026-11-18 section 5 (UNI-2687, H42, H44, H46, H49).
// English FULL and its PART templates belong to BE-21 (vsat-anh.templates.mjs).
import {assignGroups} from './groups.mjs';

const SUBJECTS = {TOAN: 'Toán', LY: 'Vật lí', HOA: 'Hóa học', SINH: 'Sinh học', SU: 'Lịch sử', DIA: 'Địa lí', VAN: 'Ngữ văn'};
const DURATION = {TOAN: 90, LY: 60, HOA: 60, SINH: 60, SU: 60, DIA: 60, VAN: null};
// Reference distribution by prompt (lệnh), specification 5.3: grades 10 / 11 / 12 (none for Địa lí, Ngữ văn) and NB / TH / VD.
const GRADES = {TOAN: [0, 17, 50], LY: [0, 25, 42], HOA: [0, 23, 44], SINH: [0, 13, 54], SU: [6, 2, 59]};
const LEVELS = {TOAN: [13, 35, 19], LY: [19, 32, 16], HOA: [14, 32, 21], SINH: [15, 28, 24],
  SU: [15, 40, 12], DIA: [20, 42, 5], VAN: [12, 33, 17]};
// Strands counted by prompt, specification 5.3 (waiting for mapping: nodeIds stay empty, H31 and D12).
const STRANDS = {
  VAN: [['Tùy bút, nhật kí', 16], ['Văn bản thông tin', 16], ['Truyện, kịch', 15], ['Văn bản nghị luận', 12], ['Thơ', 3]],
  DIA: [['Các ngành kinh tế', 26], ['Các vùng kinh tế', 17], ['Dân cư', 8], ['Tự nhiên', 10],
    ['Kỹ năng bảng số liệu, biểu đồ, Atlat', 6]]
};
// Part II reads a position from a label (Q-45 for Địa lí; Ngữ văn: a poem set and a story set).
const SLOTS = {
  VAN: [['VAN_THO'], ['VAN_TRUYEN']],
  DIA: [['DIA_BANG_SO_LIEU'], ['DIA_BIEU_DO']]
};
const NOTE = 'Cơ cấu các phần là số tạm (Q-36), chờ BA xác nhận.';

const block = (name, questionType, questionCount, points, group, extra = {}) =>
  ({name, questionType, questionCount, requiredPointsPerQuestion: points, templateGroupCode: group, ...extra});
const section = (subjectCode, blocks, extra = {}) => ({
  subjectCode, questionCount: blocks.reduce((n, item) => n + (item.omitted ? 0 : item.questionCount), 0), blocks, ...extra
});
const part = (name, sections) => {
  const counted = sections.flatMap(item => item.blocks).filter(item => !item.omitted);
  const questionCount = counted.reduce((n, item) => n + item.questionCount, 0);
  const points = counted.reduce((n, item) => n + item.questionCount * item.requiredPointsPerQuestion, 0);
  return {name, questionCount, questionCountOnPaper: questionCount, questionCountRequired: questionCount,
    participation: {mode: 'ALL'}, maxPoints: points ? Number(points.toFixed(4)) : null, sections, options: []};
};
const quota = (subject, dimension, codes, counts) => ({
  id: `vsat-${subject.toLowerCase()}-${dimension.toLowerCase()}-reference`,
  name: dimension === 'GRADE' ? 'Phân bố lớp tham khảo theo lệnh' : 'Phân bố độ khó tham khảo theo lệnh',
  dimension, basis: 'PROMPT', enforcement: 'REFERENCE', subjectCodes: [subject],
  buckets: codes.map((code, i) => ({codes: [code], count: counts[i]}))
});
const topicQuotas = subject => (STRANDS[subject] ?? []).map(([name, count], i) => ({
  id: `vsat-${subject.toLowerCase()}-strand-${i + 1}`, name, nodeIds: [], count, basis: 'PROMPT', enforcement: 'REFERENCE'
}));

function parts(subject) {
  const slots = SLOTS[subject]?.map((tagCodes, i) => ({index: i + 1, tagCodes}));
  const code = suffix => `VSAT_${subject}_${suffix}`;
  const first = section(subject, [
    block('Đúng/Sai bốn ý', 'TRUE_FALSE', 9, 6, code('TRUE_FALSE'),
      {itemShape: {statements: 4}, expectedScoring: {trueFalseMethod: 'COUNT_TABLE', trueFalseTable: [1, 2, 3, 6]}})
  ]);
  // The strands count the whole subject: they sit on the first section (H31).
  if (STRANDS[subject]) first.topicQuotas = topicQuotas(subject);
  const list = [
    part('Phần I · Đúng/Sai', [first]),
    part('Phần II · Trắc nghiệm chọn một', [section(subject, [
      block('Chọn một, 2 chùm × 3 câu', 'QUESTION_SET', 6, 6, code('SET'),
        {setCount: 2, setSize: 3, childAnswerType: 'SINGLE_CHOICE', ...(slots ? {slots} : {})})
    ])]),
    part('Phần III · Ghép đôi', [section(subject, [
      block('Ghép đôi bốn mục', 'MATCHING', 5, 6, code('MATCHING'), {itemShape: {leftItems: 4}})
    ])])
  ];
  list.push(subject === 'VAN'
    // H49: the writing part is stored omitted (no maxPoints, no count) and locked.
    ? part('Phần IV · Viết', [section(subject, [block('Viết', 'ESSAY', 1, 30, code('WRITING'), {omitted: true})])])
    : part('Phần IV · Trả lời ngắn', [section(subject, [block('Trả lời ngắn', 'SHORT_ANSWER', 5, 6, code('SHORT'))])]));
  return list;
}

const source = [];
function add(code, subject, label, duration, extra = {}) {
  const writing = subject === 'VAN';
  const questions = writing ? 20 : 25;
  const maxScore = writing ? 120 : 150;
  const entry = {code, kindCode: 'VSAT', scope: 'SUBJECT', partCode: null, subjectCode: subject, structureYear: null,
    matrixYear: null, timing: 'SHARED', durationMinutes: duration, maxScore, fixedCounts: false, fixedBlockCounts: true,
    omittedLocked: writing, expertNote: NOTE, label,
    summary: `${questions} câu, ${writing ? 62 : 67} lệnh${duration ? `, ${duration} phút` : ''}, thang ${maxScore} điểm.`,
    buildMode: 'STANDARD', isDefault: true, expected: {questions, maxPoints: maxScore},
    structure: {parts: parts(subject), examQuotas: [
      ...(GRADES[subject] ? [quota(subject, 'GRADE', ['G10', 'G11', 'G12'], GRADES[subject])] : []),
      quota(subject, 'DIFFICULTY', ['NB', 'TH', 'VD'], LEVELS[subject])
    ]}, ...extra};
  assignGroups(entry, ({block: item}) => item.templateGroupCode);
  source.push(entry);
}

for (const [subject, name] of Object.entries(SUBJECTS)) {
  add(`VSAT_SUBJECT_${subject}`, subject, `V-SAT – ${name}`, DURATION[subject],
    subject === 'VAN' ? {expertNote: 'Thời lượng nhập khi tạo (Q-35). Phần Viết lưu tham chiếu, không bật lại được, không tính vào thang.'} : {});
}
add('VSAT_SUBJECT_HOA_50', 'HOA', 'V-SAT – Hóa học – Mô phỏng đề mẫu 50 phút', 50,
  {isDefault: false, expertNote: 'Biến thể mô phỏng đề mẫu 50 phút (Q-34). ' + NOTE});

source.push({code: 'VSAT_REVIEW', kindCode: 'VSAT', scope: 'SUBJECT', partCode: null, subjectCode: null,
  structureYear: null, matrixYear: null, timing: 'SHARED', durationMinutes: null, maxScore: null, fixedCounts: false,
  expertNote: null, label: 'VSAT REVIEW', summary: null, buildMode: 'REVIEW', isDefault: true,
  expected: {questions: null, maxPoints: null}, structure: {parts: [{name: 'Review', sections: [{
    subjectCode: null, knowledgeScope: {nodeIds: [], competencyCodes: [], everyChildInScope: true}, blocks: [{}]
  }], options: []}]}});
export default source;
