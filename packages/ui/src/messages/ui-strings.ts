import type { PaginationLabels } from "../components/pagination";
import type { PasswordFieldLabels } from "../components/password-field";
import type { QuestionOutcome, QuestionType } from "../question/types";

export type UiLocale = "vi" | "en";

const NUMBER_FORMATTERS: Record<UiLocale, Intl.NumberFormat> = {
  vi: new Intl.NumberFormat("vi-VN"),
  en: new Intl.NumberFormat("en-US"),
};

export interface UiStrings {
  common: {
    close: string;
    cancel: string;
    confirm: string;
  };
  confirmDialog: {
    /** Lỗi của ô lý do khi quy tắc yêu cầu lý do mà người dùng xác nhận với ô trống. */
    reasonRequired: string;
  };
  pagination: PaginationLabels;
  passwordField: PasswordFieldLabels;
  emptyState: {
    defaultTitle: string;
  };
  /** Danh sách chọn có ô lọc (`Combobox`, `ComboboxMenu`, `Select` danh sách dài). */
  combobox: {
    filterPlaceholder: string;
    /** Tên truy cập mặc định của ô lọc khi nơi gọi không truyền. */
    filterLabel: string;
    noMatch: string;
  };
  toaster: {
    containerAriaLabel: string;
    closeButtonAriaLabel: string;
  };
  themeToggle: {
    switchToDark: string;
    switchToLight: string;
  };
  localeSwitcher: {
    label: string;
    options: Record<UiLocale, string>;
  };
  question: {
    /** Tên hiển thị của từng dạng câu trên chip của khung câu hỏi. */
    typeLabels: Record<QuestionType, string>;
    /** Nhãn kết quả (chế độ review): chữ luôn đi kèm màu (DESIGN.md mục 8). */
    outcomes: Record<QuestionOutcome, string>;
    regrading: string;
    flagForReview: string;
    flagRemove: string;
    manualGrading: string;
    chooseSingle: string;
    chooseMultiple: string;
    answerLabel: string;
    answerPlaceholder: string;
    shortAnswerNote: string;
    hintPrefix: string;
    clozeHelp: string;
    clozeFilled: (filled: number, total: number) => string;
    clozeBlankNote: string;
    clozeBlankAria: (questionNo: number, ordinal: number) => string;
    clozePlaceholder: (ordinal: number) => string;
    trueOption: string;
    falseOption: string;
    trueFalseHelp: string;
    leftColumn: string;
    rightColumn: string;
    /** Hướng dẫn của cách bấm hai lượt ("pick"): chỉ nói những gì cách đó hiện (nút Ghép với, nút X). */
    matchHelp: string;
    /** Luật dùng một lần của cách chọn trong danh sách ("select"): lựa chọn đã ghép bị làm mờ. */
    matchExclusive: string;
    /** Luật dùng một lần của cách bấm hai lượt ("pick"): lựa chọn đã ghép hiện nhãn "Đã dùng". */
    matchExclusivePick: string;
    matchReusable: string;
    matchWith: string;
    matchPlaceholder: string;
    matchClear: (leftNo: number) => string;
    matchDone: (done: number, total: number) => string;
    matchDoneNote: string;
    matchPickLeft: (leftNo: number) => string;
    matchPickRight: (letter: string, usedBy: string | null) => string;
    matchUsedTag: (leftNos: string) => string;
    matchUsedBy: (leftNo: number) => string;
    essayEditorLabel: string;
    essayPlaceholder: string;
    wordsNeeded: (count: number) => string;
    wordLimitMet: string;
    essayNote: string;
    pendingReviewNote: string;
    essayWordCount: (count: number, min: number | null) => string;
    setPassageCaption: (from: number, to: number) => string;
    points: (earned: string, max: string) => string;
    pointsSuffix: string;
    noPointsYet: string;
    maxPoints: (max: string) => string;
    solutionLabel: string;
    correctAnswerTag: string;
    yourPickTag: string;
    yourPickWrongTag: string;
    notPickedTag: string;
    correctAnswerPrefix: string;
    notAnswered: string;
    notMatched: string;
    yourEssayLabel: string;
    showFullEssay: string;
    hideFullEssay: string;
    gradingCommentLabel: string;
    criteriaLabel: string;
    criterionCol: string;
    pointsCol: string;
    itemCol: string;
    youChoseCol: string;
    answerCol: string;
    acceptedCol: string;
    blankFilledTag: string;
    countTableSummary: (correct: number, total: number, earned: string) => string;
    questionLabel: (no: number) => string;
    questionRangeLabel: (from: number, to: number) => string;
    audioPlayer: {
      regionLabel: string;
      play: string;
      playing: string;
      resume: string;
      playsRemaining: (left: number, max: number) => string;
      playsExhaustedBadge: (max: number) => string;
      exhaustedReason: string;
      partClosedReason: string;
      timeUpReason: string;
      submittedReason: string;
      evictedReason: string;
      disabledReason: string;
      autoplayBlocked: string;
      offlineWaiting: string;
      playErrorRetry: string;
      progressLabel: string;
    };
    symbolInput: {
      groupLabel: string;
      symbolNames: Record<string, string>;
    };
  };
}
export const UI_STRINGS_VI: UiStrings = {
  common: {
    close: "Đóng",
    cancel: "Hủy",
    confirm: "Xác nhận",
  },
  confirmDialog: {
    reasonRequired: "Vui lòng nhập lý do.",
  },
  pagination: {
    nav: "Phân trang",
    previous: "Trang trước",
    next: "Trang sau",
    rowsPerPage: "Số dòng mỗi trang",
    page: (page: number) => `Trang ${NUMBER_FORMATTERS.vi.format(page)}`,
    range: ({ from, to, total }) =>
      `${NUMBER_FORMATTERS.vi.format(from)}–${NUMBER_FORMATTERS.vi.format(to)} / ${NUMBER_FORMATTERS.vi.format(total)}`,
  },
  passwordField: {
    showPassword: "Hiện mật khẩu",
    hidePassword: "Ẩn mật khẩu",
    generate: "Tạo ngẫu nhiên",
    generated: "Đã tạo mật khẩu ngẫu nhiên",
    generatedShown: "Đã tạo mật khẩu ngẫu nhiên, mật khẩu đang hiện trong ô nhập",
    rulesLabel: "Điều kiện mật khẩu",
    ruleMet: "đã đạt",
    ruleUnmet: "chưa đạt",
    ruleLength: "Từ 8 đến 64 ký tự",
    ruleUpper: "Có ít nhất một chữ hoa",
    ruleLower: "Có ít nhất một chữ thường",
    ruleDigit: "Có ít nhất một chữ số",
    ruleSpecial: "Có ít nhất một ký tự đặc biệt",
  },
  emptyState: {
    defaultTitle: "Không có dữ liệu",
  },
  combobox: {
    filterPlaceholder: "Lọc…",
    filterLabel: "Lọc danh sách",
    noMatch: "Không có mục khớp",
  },
  toaster: {
    containerAriaLabel: "Thông báo",
    closeButtonAriaLabel: "Đóng thông báo",
  },
  themeToggle: {
    switchToDark: "Chuyển sang chế độ tối",
    switchToLight: "Chuyển sang chế độ sáng",
  },
  localeSwitcher: {
    label: "Chọn ngôn ngữ",
    options: {
      vi: "Tiếng Việt",
      en: "English",
    },
  },
  question: {
    typeLabels: {
      SINGLE_CHOICE: "Chọn một đáp án",
      MULTIPLE_CHOICE: "Chọn một hoặc nhiều đáp án",
      SHORT_ANSWER: "Trả lời ngắn",
      CLOZE: "Điền khuyết",
      QUESTION_SET: "Bộ câu hỏi",
      TRUE_FALSE: "Đúng/Sai",
      MATCHING: "Ghép đôi",
      ESSAY: "Tự luận",
    },
    outcomes: {
      CORRECT: "Đúng",
      PARTIAL: "Đúng một phần",
      WRONG: "Sai",
      BLANK: "Bỏ trống",
      PENDING_REVIEW: "Chờ chấm",
    },
    regrading: "Đang chấm lại",
    flagForReview: "Đánh dấu xem lại",
    flagRemove: "Bỏ đánh dấu",
    manualGrading: "Chấm tay",
    chooseSingle: "Chọn một đáp án",
    chooseMultiple: "Chọn các phương án đúng",
    answerLabel: "Đáp số",
    answerPlaceholder: "Nhập đáp số",
    shortAnswerNote: "Đáp số được so khớp đúng cách viết, nên hãy viết theo hướng dẫn nhập ở trên.",
    hintPrefix: "Hướng dẫn nhập:",
    clozeHelp: "Gõ đáp án vào từng ô trong đoạn văn. Mỗi ô là một câu riêng trong danh sách câu hỏi.",
    clozeFilled: (filled, total) =>
      `Đã điền ${NUMBER_FORMATTERS.vi.format(filled)}/${NUMBER_FORMATTERS.vi.format(total)} ô`,
    clozeBlankNote: "Ô bỏ trống tính là chưa trả lời; các ô được chấm riêng.",
    clozeBlankAria: (questionNo, ordinal) =>
      `Câu ${NUMBER_FORMATTERS.vi.format(questionNo)}, ô trống ${NUMBER_FORMATTERS.vi.format(ordinal)}`,
    clozePlaceholder: (ordinal) => `Ô ${NUMBER_FORMATTERS.vi.format(ordinal)}`,
    trueOption: "Đúng",
    falseOption: "Sai",
    trueFalseHelp: "Chọn Đúng hoặc Sai cho từng ý",
    leftColumn: "Cột trái",
    rightColumn: "Cột phải",
    matchHelp:
      "Bấm một mục ở cột trái, rồi bấm “Ghép với” ở lựa chọn bên cột phải. Bấm dấu X cạnh mục đã ghép để bỏ ghép. Dùng Tab để di chuyển, Enter hoặc Space để bấm.",
    matchExclusive:
      "Chọn lựa chọn cho từng mục trong danh sách. Mỗi lựa chọn chỉ dùng một lần: lựa chọn đã ghép bị làm mờ trong danh sách của các mục khác; bỏ ghép để dùng lại.",
    matchExclusivePick:
      "Mỗi lựa chọn chỉ dùng một lần: lựa chọn đã ghép hiện “Đã dùng”; bỏ ghép mục đó để dùng lại.",
    matchReusable: "Một lựa chọn ở cột phải có thể ghép với nhiều mục.",
    matchWith: "Ghép với",
    matchPlaceholder: "Chọn…",
    matchClear: (leftNo) => `Bỏ ghép mục ${NUMBER_FORMATTERS.vi.format(leftNo)}`,
    matchDone: (done, total) =>
      `Đã ghép ${NUMBER_FORMATTERS.vi.format(done)}/${NUMBER_FORMATTERS.vi.format(total)} mục`,
    matchDoneNote: "Mục chưa ghép tính là chưa trả lời; các cặp được chấm riêng.",
    matchPickLeft: (leftNo) => `Mục ${NUMBER_FORMATTERS.vi.format(leftNo)}`,
    matchPickRight: (letter, usedBy) =>
      usedBy ? `${letter}, đã ghép với mục ${usedBy}` : `Lựa chọn ${letter}`,
    matchUsedTag: (leftNos) => `Đã dùng · ${leftNos}`,
    matchUsedBy: (leftNo) =>
      `Lựa chọn này đã ghép với mục ${NUMBER_FORMATTERS.vi.format(leftNo)}. Bỏ ghép mục ${NUMBER_FORMATTERS.vi.format(leftNo)} trước nếu muốn dùng cho mục khác.`,
    essayEditorLabel: "Bài viết của bạn",
    essayPlaceholder: "Viết bài của bạn tại đây",
    wordsNeeded: (count) => `Còn ${NUMBER_FORMATTERS.vi.format(count)} chữ nữa để đạt yêu cầu.`,
    wordLimitMet: "Đã đạt yêu cầu số chữ",
    essayNote:
      "Bài viết chỉ nhận chữ và định dạng chữ (đậm, nghiêng, gạch chân, danh sách), không chèn ảnh. Nội dung được lưu tự động trong lúc bạn viết.",
    pendingReviewNote: "Câu này do người chấm chấm sau khi bạn nộp bài. Điểm tổng của lượt hiện khi người chấm chấm xong.",
    essayWordCount: (count, min) =>
      min === null
        ? `${NUMBER_FORMATTERS.vi.format(count)} chữ`
        : `${NUMBER_FORMATTERS.vi.format(count)} / tối thiểu ${NUMBER_FORMATTERS.vi.format(min)} chữ`,
    setPassageCaption: (from, to) =>
      `Ngữ liệu chung cho câu ${NUMBER_FORMATTERS.vi.format(from)}–${NUMBER_FORMATTERS.vi.format(to)}`,
    points: (earned, max) => `${earned} / ${max} điểm`,
    pointsSuffix: "điểm",
    noPointsYet: "Chưa có điểm",
    maxPoints: (max) => `tối đa ${max} điểm`,
    solutionLabel: "Lời giải",
    correctAnswerTag: "Đáp án đúng",
    yourPickTag: "Bạn chọn",
    yourPickWrongTag: "Bạn chọn · Sai",
    notPickedTag: "Bạn không chọn",
    correctAnswerPrefix: "Đáp án:",
    notAnswered: "Chưa trả lời",
    notMatched: "Chưa ghép",
    yourEssayLabel: "Bài viết của bạn",
    showFullEssay: "Xem toàn bộ bài viết",
    hideFullEssay: "Thu gọn bài viết",
    gradingCommentLabel: "Nhận xét của người chấm",
    criteriaLabel: "Điểm theo tiêu chí",
    criterionCol: "Tiêu chí",
    pointsCol: "Điểm",
    itemCol: "Mục",
    youChoseCol: "Bạn chọn",
    answerCol: "Đáp án",
    acceptedCol: "Đáp án được chấp nhận",
    blankFilledTag: "bỏ trống",
    countTableSummary: (correct, total, earned) =>
      `Đúng ${NUMBER_FORMATTERS.vi.format(correct)}/${NUMBER_FORMATTERS.vi.format(total)} ý → ${earned} điểm`,
    questionLabel: (no) => `Câu ${NUMBER_FORMATTERS.vi.format(no)}`,
    questionRangeLabel: (from, to) =>
      `Câu ${NUMBER_FORMATTERS.vi.format(from)}–${NUMBER_FORMATTERS.vi.format(to)}`,
    audioPlayer: {
      regionLabel: "Trình phát audio",
      play: "Nghe",
      playing: "Đang phát…",
      resume: "Phát lại",
      playsRemaining: (left, max) =>
        `Còn ${NUMBER_FORMATTERS.vi.format(left)}/${NUMBER_FORMATTERS.vi.format(max)} lần nghe`,
      playsExhaustedBadge: (max) =>
        `Đã hết lượt nghe (0/${NUMBER_FORMATTERS.vi.format(max)})`,
      exhaustedReason: "Bạn đã dùng hết số lần nghe cho tệp audio này.",
      partClosedReason: "Phần thi đã đóng, không thể nghe tiếp audio.",
      timeUpReason: "Đã hết giờ làm bài, audio đã dừng phát.",
      submittedReason: "Bài thi đã nộp, audio đã dừng phát.",
      evictedReason: "Phiên làm bài đã chuyển sang thiết bị khác, audio đã dừng.",
      disabledReason: "Trình phát audio đang tạm khóa.",
      autoplayBlocked:
        "Trình duyệt chặn tự động phát. Bấm Phát lại để tiếp tục mà không mất lượt nghe.",
      offlineWaiting: "Mất kết nối mạng, đang chờ kết nối lại để phát tiếp.",
      playErrorRetry: "Không thể phát audio lúc này. Bấm lại để thử tiếp.",
      progressLabel: "Tiến độ phát audio",
    },
    symbolInput: {
      groupLabel: "Thanh chèn ký hiệu",
      symbolNames: {
        plus: "Dấu cộng (+)",
        minus: "Dấu trừ (-)",
        lparen: "Mở ngoặc (",
        rparen: "Đóng ngoặc )",
        "charge-2plus": "Điện tích 2+",
        "charge-3plus": "Điện tích 3+",
        "charge-2minus": "Điện tích 2-",
        "charge-3minus": "Điện tích 3-",
        "sub-2": "Chỉ số dưới 2",
        "sub-3": "Chỉ số dưới 3",
        "sub-4": "Chỉ số dưới 4",
        "arrow-right": "Mũi tên phản ứng (->)",
      },
    },
  },
};

export const UI_STRINGS_EN: UiStrings = {
  common: {
    close: "Close",
    cancel: "Cancel",
    confirm: "Confirm",
  },
  confirmDialog: {
    reasonRequired: "Please enter a reason.",
  },
  pagination: {
    nav: "Pagination",
    previous: "Previous page",
    next: "Next page",
    rowsPerPage: "Rows per page",
    page: (page: number) => `Page ${NUMBER_FORMATTERS.en.format(page)}`,
    range: ({ from, to, total }) =>
      `${NUMBER_FORMATTERS.en.format(from)}–${NUMBER_FORMATTERS.en.format(to)} / ${NUMBER_FORMATTERS.en.format(total)}`,
  },
  passwordField: {
    showPassword: "Show password",
    hidePassword: "Hide password",
    generate: "Generate random",
    generated: "Random password generated",
    generatedShown: "Random password generated and shown in the field",
    rulesLabel: "Password requirements",
    ruleMet: "met",
    ruleUnmet: "not met",
    ruleLength: "8 to 64 characters",
    ruleUpper: "At least one uppercase letter",
    ruleLower: "At least one lowercase letter",
    ruleDigit: "At least one digit",
    ruleSpecial: "At least one special character",
  },
  emptyState: {
    defaultTitle: "No data",
  },
  combobox: {
    filterPlaceholder: "Filter…",
    filterLabel: "Filter list",
    noMatch: "No matching items",
  },
  toaster: {
    containerAriaLabel: "Notifications",
    closeButtonAriaLabel: "Close toast",
  },
  themeToggle: {
    switchToDark: "Switch to dark mode",
    switchToLight: "Switch to light mode",
  },
  localeSwitcher: {
    label: "Change language",
    options: {
      vi: "Tiếng Việt",
      en: "English",
    },
  },
  question: {
    typeLabels: {
      SINGLE_CHOICE: "Single choice",
      MULTIPLE_CHOICE: "Multiple choice",
      SHORT_ANSWER: "Short answer",
      CLOZE: "Fill in the blanks",
      QUESTION_SET: "Question set",
      TRUE_FALSE: "True/False",
      MATCHING: "Matching",
      ESSAY: "Essay",
    },
    outcomes: {
      CORRECT: "Correct",
      PARTIAL: "Partially correct",
      WRONG: "Wrong",
      BLANK: "Blank",
      PENDING_REVIEW: "Pending grading",
    },
    regrading: "Regrading",
    flagForReview: "Flag for review",
    flagRemove: "Remove flag",
    manualGrading: "Manually graded",
    chooseSingle: "Select one answer",
    chooseMultiple: "Select all correct answers",
    answerLabel: "Answer",
    answerPlaceholder: "Type the answer",
    shortAnswerNote: "The answer is matched literally, so follow the input hint above.",
    hintPrefix: "Input hint:",
    clozeHelp: "Type the answer into each blank in the passage. Each blank is its own question.",
    clozeFilled: (filled, total) =>
      `${NUMBER_FORMATTERS.en.format(filled)}/${NUMBER_FORMATTERS.en.format(total)} filled`,
    clozeBlankNote: "An empty blank counts as unanswered; blanks are graded separately.",
    clozeBlankAria: (questionNo, ordinal) =>
      `Question ${NUMBER_FORMATTERS.en.format(questionNo)}, blank ${NUMBER_FORMATTERS.en.format(ordinal)}`,
    clozePlaceholder: (ordinal) => `Blank ${NUMBER_FORMATTERS.en.format(ordinal)}`,
    trueOption: "True",
    falseOption: "False",
    trueFalseHelp: "Choose True or False for each statement",
    leftColumn: "Left column",
    rightColumn: "Right column",
    matchHelp:
      "Click an item on the left, then “Match with” on a choice on the right. Click the X next to a matched item to unmatch it. Use Tab to move, Enter or Space to activate.",
    matchExclusive:
      "Pick a choice for each item from its list. Each choice can be used once: a matched choice is dimmed in the other items’ lists; unmatch it to reuse it.",
    matchExclusivePick:
      "Each choice can be used once: a matched choice shows “Used”; unmatch that item to reuse it.",
    matchReusable: "A right-column choice can match multiple items.",
    matchWith: "Match with",
    matchPlaceholder: "Select…",
    matchClear: (leftNo) => `Unmatch item ${NUMBER_FORMATTERS.en.format(leftNo)}`,
    matchDone: (done, total) =>
      `${NUMBER_FORMATTERS.en.format(done)}/${NUMBER_FORMATTERS.en.format(total)} matched`,
    matchDoneNote: "An unmatched item counts as unanswered; pairs are graded separately.",
    matchPickLeft: (leftNo) => `Item ${NUMBER_FORMATTERS.en.format(leftNo)}`,
    matchPickRight: (letter, usedBy) =>
      usedBy ? `${letter}, already matched with item ${usedBy}` : `Choice ${letter}`,
    matchUsedTag: (leftNos) => `Used · ${leftNos}`,
    matchUsedBy: (leftNo) =>
      `This choice is already matched with item ${NUMBER_FORMATTERS.en.format(leftNo)}. Unmatch item ${NUMBER_FORMATTERS.en.format(leftNo)} first to use it elsewhere.`,
    essayEditorLabel: "Your essay",
    essayPlaceholder: "Write your essay here",
    wordsNeeded: (count) => `${NUMBER_FORMATTERS.en.format(count)} more words to reach the minimum.`,
    wordLimitMet: "Word minimum reached",
    essayNote:
      "The essay accepts text and text formatting only (bold, italic, underline, lists); no images. Content is autosaved while you write.",
    pendingReviewNote:
      "This question is graded by a human after you submit. The attempt total appears once grading finishes.",
    essayWordCount: (count, min) =>
      min === null
        ? `${NUMBER_FORMATTERS.en.format(count)} words`
        : `${NUMBER_FORMATTERS.en.format(count)} / at least ${NUMBER_FORMATTERS.en.format(min)} words`,
    setPassageCaption: (from, to) =>
      `Shared passage for questions ${NUMBER_FORMATTERS.en.format(from)}–${NUMBER_FORMATTERS.en.format(to)}`,
    points: (earned, max) => `${earned} / ${max} points`,
    pointsSuffix: "points",
    noPointsYet: "No points yet",
    maxPoints: (max) => `up to ${max} points`,
    solutionLabel: "Solution",
    correctAnswerTag: "Correct answer",
    yourPickTag: "Your choice",
    yourPickWrongTag: "Your choice · Wrong",
    notPickedTag: "Not chosen",
    correctAnswerPrefix: "Answer:",
    notAnswered: "Not answered",
    notMatched: "Not matched",
    yourEssayLabel: "Your essay",
    showFullEssay: "Show full essay",
    hideFullEssay: "Collapse essay",
    gradingCommentLabel: "Grader comment",
    criteriaLabel: "Points by criterion",
    criterionCol: "Criterion",
    pointsCol: "Points",
    itemCol: "Item",
    youChoseCol: "You chose",
    answerCol: "Answer",
    acceptedCol: "Accepted answers",
    blankFilledTag: "blank",
    countTableSummary: (correct, total, earned) =>
      `${NUMBER_FORMATTERS.en.format(correct)}/${NUMBER_FORMATTERS.en.format(total)} correct → ${earned} points`,
    questionLabel: (no) => `Question ${NUMBER_FORMATTERS.en.format(no)}`,
    questionRangeLabel: (from, to) =>
      `Question ${NUMBER_FORMATTERS.en.format(from)}–${NUMBER_FORMATTERS.en.format(to)}`,
    audioPlayer: {
      regionLabel: "Audio player",
      play: "Play",
      playing: "Playing…",
      resume: "Resume playback",
      playsRemaining: (left, max) =>
        `${NUMBER_FORMATTERS.en.format(left)}/${NUMBER_FORMATTERS.en.format(max)} plays left`,
      playsExhaustedBadge: (max) =>
        `No plays left (0/${NUMBER_FORMATTERS.en.format(max)})`,
      exhaustedReason: "You have used all plays for this audio.",
      partClosedReason: "This part is closed; audio playback is unavailable.",
      timeUpReason: "Time is up; audio playback has stopped.",
      submittedReason: "Attempt submitted; audio playback has stopped.",
      evictedReason: "Session moved to another device; audio playback stopped.",
      disabledReason: "Audio player is currently disabled.",
      autoplayBlocked:
        "Browser blocked automatic playback. Click Resume playback to continue without using a play.",
      offlineWaiting: "Connection lost; waiting to reconnect to resume playback.",
      playErrorRetry: "Could not start audio right now. Click again to retry.",
      progressLabel: "Audio playback progress",
    },
    symbolInput: {
      groupLabel: "Symbol input bar",
      symbolNames: {
        plus: "Plus sign (+)",
        minus: "Minus sign (-)",
        lparen: "Left parenthesis (",
        rparen: "Right parenthesis )",
        "charge-2plus": "Charge 2+",
        "charge-3plus": "Charge 3+",
        "charge-2minus": "Charge 2-",
        "charge-3minus": "Charge 3-",
        "sub-2": "Subscript 2",
        "sub-3": "Subscript 3",
        "sub-4": "Subscript 4",
        "arrow-right": "Reaction arrow (->)",
      },
    },
  },
};

export const UI_STRINGS_BY_LOCALE: Record<UiLocale, UiStrings> = {
  vi: UI_STRINGS_VI,
  en: UI_STRINGS_EN,
};
