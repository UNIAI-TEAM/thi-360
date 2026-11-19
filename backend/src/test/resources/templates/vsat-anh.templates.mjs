// V-SAT English, addendum specification 2026-11-18 section 5.2 (UNI-2693, H40, H41, H44, H46).
// Option B (D17): the FULL paper is one ExamPart with four display segments; the shared reading
// set spans questions 8-15 across segments I and II. PART exams have their own templates P2-P4
// with subject_code ANH, so Part I is never created from the single-part subject template.

const ANH = 'ANH';

const block = (name, questionType, questionCount, points, extra = {}) =>
  ({name, questionType, questionCount, requiredPointsPerQuestion: points, ...extra});
const section = (blocks, extra = {}) => ({
  subjectCode: ANH, questionCount: blocks.reduce((n, item) => n + (item.omitted ? 0 : item.questionCount), 0),
  blocks, ...extra
});
const part = (name, sections, extra = {}) => {
  const counted = sections.flatMap(item => item.blocks).filter(item => !item.omitted);
  const questionCount = counted.reduce((n, item) => n + item.questionCount, 0);
  const points = counted.reduce((n, item) => n + item.questionCount * item.requiredPointsPerQuestion, 0);
  return {name, questionCount, questionCountOnPaper: questionCount, questionCountRequired: questionCount,
    participation: {mode: 'ALL'}, maxPoints: points ? Number(points.toFixed(4)) : null, sections, options: [], ...extra};
};
const segment = (code, name, fromPosition, toPosition, commandCount, maxPoints) =>
  ({code, name, fromPosition, toPosition, commandCount, maxPoints});

// Specification 5.3: English strands counted by prompt at reference level; nodeIds stay empty
// until the mapping lands (H31, D12). They sit on the first section (whole-subject rows, H31).
const STRANDS = [['Đọc hiểu', 42], ['Giao tiếp qua văn bản', 4], ['Ngữ pháp và từ vựng', 21]];
const topicQuotas = STRANDS.map(([name, count], i) => ({
  id: `vsat-anh-strand-${i + 1}`, name, nodeIds: [], count, basis: 'PROMPT', enforcement: 'REFERENCE'
}));
const examQuotas = [
  {id: 'vsat-anh-difficulty-reference', name: 'Phân bố độ khó tham khảo theo lệnh',
    dimension: 'DIFFICULTY', basis: 'PROMPT', enforcement: 'REFERENCE', subjectCodes: [ANH],
    buckets: ['NB', 'TH', 'VD'].map((code, i) => ({codes: [code], count: [21, 39, 7][i]}))}
];

const source = [];

source.push({code: 'VSAT_SUBJECT_ANH', kindCode: 'VSAT', scope: 'SUBJECT', partCode: null, subjectCode: ANH,
  structureYear: null, matrixYear: null, timing: 'SHARED', durationMinutes: 60, maxScore: 150,
  fixedCounts: false, fixedBlockCounts: true, omittedLocked: false,
  expertNote: 'Phần I-IV là phân đoạn hiển thị của một phần duy nhất (D17); câu 8-15 dùng chung một bài đọc (Q-36, chờ BA).',
  label: 'V-SAT – Tiếng Anh', summary: '25 câu, 67 lệnh, 60 phút, thang 150 điểm.',
  buildMode: 'STANDARD', isDefault: true, expected: {questions: 25, maxPoints: 150},
  structure: {parts: [
    part('Tiếng Anh', [
      (() => { const first = section([
        block('Đúng/Sai độc lập', 'TRUE_FALSE', 7, 6,
          {itemShape: {statements: 4}, expectedScoring: {trueFalseMethod: 'COUNT_TABLE', trueFalseTable: [1, 2, 3, 6]}}),
        block('Bài đọc dùng chung – câu 8–15', 'QUESTION_SET', 8, 6,
          {setCount: 1, setSize: 8, childConditions: [
            {from: 1, to: 2, nodeIds: [], answerType: 'TRUE_FALSE', itemShape: {statements: 4}},
            {from: 3, to: 8, nodeIds: [], answerType: 'SINGLE_CHOICE'}]}),
        block('Ghép đôi bốn mục', 'MATCHING', 5, 6, {itemShape: {leftItems: 4}}),
        block('Đọc điền một đoạn', 'CLOZE', 5, 6, {setCount: 1, setSize: 5})
      ]); first.topicQuotas = topicQuotas; return first; })()
    ], {displaySegments: [
      segment('P1', 'Phần I', 1, 9, 36, 54),
      segment('P2', 'Phần II', 10, 15, 6, 36),
      segment('P3', 'Phần III', 16, 20, 20, 30),
      segment('P4', 'Phần IV', 21, 25, 5, 30)
    ]})
  ], examQuotas}});

// H41: the part templates carry subject_code ANH; a subject that has them is never cut for a part exam.
function partTemplate(partCode, name, block, questionCount, points) {
  return {code: `VSAT_PART_ANH_${partCode}`, kindCode: 'VSAT', scope: 'PART', partCode,
    subjectCode: ANH, structureYear: null, matrixYear: null, timing: 'SHARED',
    durationMinutes: null, maxScore: null, fixedCounts: false, fixedBlockCounts: false,
    omittedLocked: false, expertNote: null, label: `V-SAT – Tiếng Anh – ${name}`,
    summary: `${questionCount} câu, thang ${points} điểm.`,
    buildMode: 'STANDARD', isDefault: true, expected: {questions: questionCount, maxPoints: points},
    structure: {parts: [part(name, [section([block])])], examQuotas: []}};
}

source.push(
  partTemplate('P2', 'Phần II · Trắc nghiệm chọn một',
    block('Chọn một, 1 chùm × 6 câu', 'QUESTION_SET', 6, 6,
      {setCount: 1, setSize: 6, childAnswerType: 'SINGLE_CHOICE'}), 6, 36),
  partTemplate('P3', 'Phần III · Ghép đôi',
    block('Ghép đôi bốn mục', 'MATCHING', 5, 6, {itemShape: {leftItems: 4}}), 5, 30),
  partTemplate('P4', 'Phần IV · Đọc điền',
    block('Đọc điền một đoạn', 'CLOZE', 5, 6, {setCount: 1, setSize: 5}), 5, 30)
);

export default source;
