// H-SCA structures, addendum specification 2026-11-18 section 4 (UNI-2687, H36 to H46).
import {assignGroups} from './groups.mjs';

const SUBJECTS = {TOAN: 'Toán', LY: 'Vật lí', HOA: 'Hóa học', SINH: 'Sinh học'};
// Reference distribution of the whole subject, specification 4.1: grades 10 / 11 / 12 and levels NB / TH / VD.
const GRADES = {TOAN: [0, 14, 26], LY: [0, 12, 28], HOA: [10, 18, 12], SINH: [5, 5, 30]};
const LEVELS = {TOAN: [9, 15, 16], LY: [11, 14, 15], HOA: [12, 15, 13], SINH: [10, 16, 14]};
// The sample-paper variant: per row (single choice, multiple choice, data set, short answer), NB / TH / VD.
const SAMPLE = {
  TOAN: [[9, 10, 1], [0, 1, 4], [0, 2, 3], [0, 2, 8]],
  LY: [[11, 9, 0], [0, 3, 2], [0, 2, 3], [0, 0, 10]],
  HOA: [[12, 8, 0], [0, 4, 1], [0, 2, 3], [0, 1, 9]],
  SINH: [[10, 9, 1], [0, 4, 1], [0, 2, 3], [0, 1, 9]]
};
const QUOTA_NOTE = 'Định hướng của BA: câu lớp 12 chiếm 70–80% số câu; mức đối chiếu nạp theo số đề mẫu (Q-33). ' +
  'Toán 26/40 và Hóa học 12/40 dưới 28 câu. Chờ BA xác nhận.';

const values = (codes, counts) => counts.map((value, i) => ({code: codes[i], value}));
const matrix = levels => ({
  mode: 'SEPARATE', unit: 'COUNT', basis: 'QUESTION', gradeEnforcement: 'REFERENCE', difficultyEnforcement: 'REFERENCE',
  byGrade: [], byDifficulty: values(['NB', 'TH', 'VD'], levels)
});
const quota = (subject, dimension, codes, counts) => ({
  id: `hsca-${subject.toLowerCase()}-${dimension.toLowerCase()}-reference`,
  name: dimension === 'GRADE' ? 'Phân bố lớp tham khảo' : 'Phân bố độ khó tham khảo',
  dimension, basis: 'QUESTION', enforcement: 'REFERENCE', subjectCodes: [subject],
  buckets: codes.map((code, i) => ({codes: [code], count: counts[i]}))
});
const block = (name, questionType, questionCount, points, group, extra = {}) =>
  ({name, questionType, questionCount, requiredPointsPerQuestion: points, templateGroupCode: group, ...extra});
const single = (name, count, points, group, extra = {}) =>
  block(name, 'SINGLE_CHOICE', count, points, group, {itemShape: {options: 4}, ...extra});
const set = (name, count, size, points, group, extra = {}) =>
  block(name, 'QUESTION_SET', count * size, points, group,
    {setCount: count, setSize: size, childAnswerType: 'SINGLE_CHOICE', ...extra});
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
const template = (code, subject, label, duration, maxScore, questions, parts, extra = {}) => {
  const entry = {code, kindCode: 'HSCA', scope: 'SUBJECT', partCode: null, subjectCode: subject, structureYear: null,
    matrixYear: null, timing: 'SHARED', durationMinutes: duration, maxScore, fixedCounts: false,
    fixedBlockCounts: false, omittedLocked: false, expertNote: null, label,
    summary: `${questions} câu${duration ? `, ${duration} phút` : ''}, thang ${maxScore} điểm.`,
    buildMode: 'STANDARD', isDefault: true, expected: {questions, maxPoints: maxScore},
    structure: {parts: structuredClone(parts), examQuotas: []}, ...extra};
  assignGroups(entry, ({block: item}) => item.templateGroupCode);
  return entry;
};

const source = [];

// Toán, Vật lí, Hóa học, Sinh học: 40 câu, 90 phút chung, 10 điểm, khóa dòng.
for (const [subject, name] of Object.entries(SUBJECTS)) {
  for (const sample of [false, true]) {
    const rows = SAMPLE[subject];
    const matrixOf = index => sample ? {matrix: matrix(rows[index])} : {};
    const parts = [
      part('Phần 1 · Trắc nghiệm', [section(subject, [
        single('Trắc nghiệm chọn một', 20, .25, `HSCA_${subject}_SINGLE`, matrixOf(0)),
        block('Trắc nghiệm chọn nhiều', 'MULTIPLE_CHOICE', 5, .25, `HSCA_${subject}_MULTI`,
          {...matrixOf(1), expectedScoring: {multipleChoiceRule: 'PARTIAL'}})
      ])]),
      part('Phần 2 · Dữ liệu tổng hợp', [section(subject, [
        block('Chùm dữ liệu tổng hợp', 'QUESTION_SET', 5, .25, `HSCA_${subject}_DATA_SET`, matrixOf(2))
      ])]),
      part('Phần 3 · Điền đáp án', [section(subject, [
        block('Trả lời ngắn', 'SHORT_ANSWER', 10, .25, `HSCA_${subject}_SHORT`, matrixOf(3))
      ])])
    ];
    const entry = template(sample ? `HSCA_SUBJECT_${subject}_SAMPLE` : `HSCA_SUBJECT_${subject}`, subject,
      `H-SCA – ${name}${sample ? ' – Sát đề minh họa' : ''}`, 90, 10, 40, parts,
      {fixedBlockCounts: true, isDefault: !sample, expertNote: QUOTA_NOTE});
    entry.structure.examQuotas = [
      quota(subject, 'GRADE', ['G10', 'G11', 'G12'], GRADES[subject]),
      quota(subject, 'DIFFICULTY', ['NB', 'TH', 'VD'], LEVELS[subject])
    ];
    source.push(entry);
  }
}

// Ngữ văn: ba chùm Đọc hiểu, hai phần viết lưu tham chiếu (H43).
const readingMatrix = [[2, 3, 0], [2, 3, 2], [2, 4, 2]];
source.push(template('HSCA_SUBJECT_VAN', 'VAN', 'H-SCA – Ngữ văn', null, 20, 20, [
  part('Phần 1 · Đọc hiểu', [section('VAN', [
    set('Văn bản thông tin', 1, 5, 1, 'HSCA_VAN_INFORMATION',
      {tagCodes: ['VAN_THONG_TIN'], itemShape: {options: 4}, matrix: matrix(readingMatrix[0])}),
    set('Văn bản nghị luận', 1, 7, 1, 'HSCA_VAN_ARGUMENT',
      {tagCodes: ['VAN_NGHI_LUAN'], itemShape: {options: 4}, matrix: matrix(readingMatrix[1])}),
    set('Thơ', 1, 8, 1, 'HSCA_VAN_POETRY',
      {tagCodes: ['VAN_THO'], itemShape: {options: 4}, matrix: matrix(readingMatrix[2])})
  ])]),
  part('Phần 2 · Viết đoạn văn', [section('VAN', [
    block('Viết đoạn văn', 'ESSAY', 1, null, 'HSCA_VAN_PARAGRAPH', {omitted: true})
  ])]),
  part('Phần 3 · Viết bài văn', [section('VAN', [
    block('Viết bài văn', 'ESSAY', 1, null, 'HSCA_VAN_ESSAY', {omitted: true})
  ])])
], {omittedLocked: true,
  expertNote: 'Hai phần viết lưu để tham chiếu, không bật lại được. Điểm 1 mỗi câu, thang bằng số câu (Q-39).'}));

// Tiếng Anh: Nghe (audio ở câu gốc) và Đọc (không audio); mọi câu một điểm.
source.push(template('HSCA_SUBJECT_ANH', 'ANH', 'H-SCA – Tiếng Anh', null, 75, 75, [
  part('Nghe', [section('ANH', [
    block('Nghe – câu đơn', 'SINGLE_CHOICE', 8, 1, 'HSCA_ANH_LISTEN_SINGLE'),
    set('Nghe – chùm 4 câu', 3, 4, 1, 'HSCA_ANH_LISTEN_SET_4'),
    set('Nghe – chùm 5 câu', 3, 5, 1, 'HSCA_ANH_LISTEN_SET_5')
  ], {audioRequirement: 'REQUIRED'})]),
  part('Đọc', [section('ANH', [
    set('Đọc – chùm 10 câu', 4, 10, 1, 'HSCA_ANH_READING_SET', {itemShape: {options: 4}})
  ], {audioRequirement: 'FORBIDDEN'})])
], {expertNote: 'Điểm 1 mỗi câu, thang bằng số câu (Q-39). Phần Nghe không ràng buộc số phương án (HS §6.1).'}));

source.push({code: 'HSCA_REVIEW', kindCode: 'HSCA', scope: 'SUBJECT', partCode: null, subjectCode: null,
  structureYear: null, matrixYear: null, timing: 'SHARED', durationMinutes: null, maxScore: null, fixedCounts: false,
  expertNote: null, label: 'HSCA REVIEW', summary: null, buildMode: 'REVIEW', isDefault: true,
  expected: {questions: null, maxPoints: null}, structure: {parts: [{name: 'Review', sections: [{
    subjectCode: null, knowledgeScope: {nodeIds: [], competencyCodes: [], everyChildInScope: true}, blocks: [{}]
  }], options: []}]}});
export default source;
