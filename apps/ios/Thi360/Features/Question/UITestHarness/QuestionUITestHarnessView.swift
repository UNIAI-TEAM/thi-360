#if THI360_DEV
import SwiftUI

enum QuestionUITestScenario: String {
    case shortAnswerSmartPunctuation
    case numericKeyRowFraction
    case clozeKeyboardScroll
    case questionSetSplitView
    case vietnameseTelexEssay

    static func fromLaunchArguments(_ arguments: [String] = ProcessInfo.processInfo.arguments) -> QuestionUITestScenario? {
        guard AppEnvironment.current.isDev,
              let index = arguments.firstIndex(of: "-uitest-question-harness"),
              index + 1 < arguments.count
        else {
            return nil
        }
        return QuestionUITestScenario(rawValue: arguments[index + 1])
    }
}

struct QuestionUITestHarnessView: View {
    let scenario: QuestionUITestScenario
    @Bindable var preferences: AppPreferences

    var body: some View {
        switch scenario {
        case .shortAnswerSmartPunctuation:
            ShortAnswerSmartPunctuationHarnessView(locale: preferences.locale)
        case .numericKeyRowFraction:
            NumericKeyRowFractionHarnessView(locale: preferences.locale)
        case .clozeKeyboardScroll:
            ClozeKeyboardScrollHarnessView(locale: preferences.locale)
        case .questionSetSplitView:
            QuestionSetSplitViewHarnessView(locale: preferences.locale)
        case .vietnameseTelexEssay:
            VietnameseTelexEssayHarnessView(locale: preferences.locale)
        }
    }
}

private struct ShortAnswerSmartPunctuationHarnessView: View {
    let locale: AppLocale
    @State private var answer: AnswerValue?

    private let item = QuestionItem.shortAnswer(
        id: "harness-short-text",
        displayNo: 1,
        points: 1,
        stem: "<p>Nhập đúng ký tự theo đề bài.</p>",
        answerFormat: .text,
        inputHint: "Giữ nguyên dấu ngoặc kép và dấu gạch nối",
        answers: []
    )

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(answer?.shortAnswerText ?? "")
                        .font(.caption.monospaced())
                        .accessibilityIdentifier("harness.question.answerEcho")

                    QuestionView(
                        item: item,
                        answer: answer,
                        mode: .answering,
                        onAnswer: { next in answer = next },
                        locale: locale
                    )
                }
                .padding(16)
            }
            .navigationTitle("Short Answer Harness")
        }
    }
}

private struct NumericKeyRowFractionHarnessView: View {
    let locale: AppLocale
    @State private var answer: AnswerValue?

    private let item = QuestionItem.shortAnswer(
        id: "harness-short-fraction",
        displayNo: 2,
        points: 1,
        stem: "<p>Rút gọn phân số và nhập kết quả dạng phân số tối giản.</p>",
        answerFormat: .fraction,
        inputHint: "Nhập phân số dạng a/b",
        answers: []
    )

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(answer?.shortAnswerText ?? "")
                        .font(.caption.monospaced())
                        .accessibilityIdentifier("harness.question.answerEcho")

                    QuestionView(
                        item: item,
                        answer: answer,
                        mode: .answering,
                        onAnswer: { next in answer = next },
                        locale: locale
                    )
                }
                .padding(16)
            }
            .navigationTitle("Numeric Row Harness")
        }
    }
}

private struct ClozeKeyboardScrollHarnessView: View {
    let locale: AppLocale
    @State private var answer: AnswerValue?

    private let item = QuestionItem.cloze(
        id: "harness-cloze-scroll",
        displayNo: 22,
        points: 2,
        stem: "<p>Điền kết quả vào các ô trống.</p>",
        passage: "<p>Ở điều kiện tiêu chuẩn, nước sôi ở [[b1]] độ C và đóng băng ở [[b2]] độ C.</p>",
        blanks: [
            ClozeBlank(id: "b1"),
            ClozeBlank(id: "b2")
        ],
        blankNumberStart: 22
    )

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(answer?.clozeBlanks["b1"] ?? "")
                        .font(.caption.monospaced())
                        .accessibilityIdentifier("harness.question.answerEcho")

                    ForEach(1...10, id: \.self) { idx in
                        Text("Đoạn văn mở đầu số \(idx) nhằm đẩy câu hỏi điền khuyết xuống nửa dưới màn hình.")
                            .font(.body)
                            .padding(.vertical, 6)
                    }

                    QuestionView(
                        item: item,
                        answer: answer,
                        mode: .answering,
                        onAnswer: { next in answer = next },
                        locale: locale
                    )

                    Spacer(minLength: 80)
                }
                .padding(16)
            }
            .accessibilityIdentifier("harness.question.clozeScrollView")
            .navigationTitle("Cloze Keyboard Harness")
        }
    }
}

private struct QuestionSetSplitViewHarnessView: View {
    let locale: AppLocale
    @State private var answers: [String: AnswerValue] = [:]
    @State private var lastAnsweredId: String = ""
    @State private var forcedSizeClass: UserInterfaceSizeClass?
    @Environment(\.horizontalSizeClass) private var systemSizeClass

    private var effectiveSizeClass: UserInterfaceSizeClass? {
        forcedSizeClass ?? systemSizeClass
    }

    private var item: QuestionItem {
        let paragraphs = (1...14).map { idx in
            "<p>Đoạn ngữ liệu số \(idx): Năng lượng mặt trời và gió là hai nguồn năng lượng tái tạo chủ lực trong quá trình chuyển dịch xanh của lưới điện hiện đại.</p>"
        }.joined()

        let child1 = QuestionItem.singleChoice(
            id: "set-child-1",
            displayNo: 10,
            points: 1,
            stem: "<p>Chủ đề chính của đoạn ngữ liệu là gì?</p>",
            optionOrder: nil,
            options: [
                ChoiceOption(id: "opt-a", content: "<p>Năng lượng tái tạo</p>"),
                ChoiceOption(id: "opt-b", content: "<p>Nhiệt điện than</p>")
            ]
        )

        let child2 = QuestionItem.shortAnswer(
            id: "set-child-2",
            displayNo: 11,
            points: 1,
            stem: "<p>Nhập từ khóa trọng tâm của đoạn đọc hiểu.</p>",
            answerFormat: .text,
            inputHint: nil,
            answers: []
        )

        return .questionSet(
            id: "harness-set",
            displayNo: 10,
            points: 2,
            stem: "<p>Đọc đoạn văn sau và trả lời các câu hỏi bên dưới.</p>",
            passage: paragraphs,
            blankNumberStart: nil,
            children: [child1, child2]
        )
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 8) {
                HStack(spacing: 12) {
                    Text("LAST:\(lastAnsweredId)|C2:\(answers["set-child-2"]?.shortAnswerText ?? "")")
                        .font(.caption.monospaced())
                        .accessibilityIdentifier("harness.question.setEcho")

                    Spacer()

                    Button("Toggle Size Class") {
                        let current = effectiveSizeClass ?? .regular
                        forcedSizeClass = (current == .regular) ? .compact : .regular
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("harness.question.toggleSizeClass")
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)

                Group {
                    if effectiveSizeClass == .regular {
                        QuestionView(
                            item: item,
                            answers: answers,
                            mode: .answering,
                            onAnswer: { itemId, value in
                                lastAnsweredId = itemId
                                answers[itemId] = value
                            },
                            locale: locale
                        )
                        .padding(16)
                    } else {
                        ScrollView {
                            QuestionView(
                                item: item,
                                answers: answers,
                                mode: .answering,
                                onAnswer: { itemId, value in
                                    lastAnsweredId = itemId
                                    answers[itemId] = value
                                },
                                locale: locale
                            )
                            .padding(16)
                        }
                    }
                }
                .environment(\.horizontalSizeClass, effectiveSizeClass)
            }
            .navigationTitle("Question Set Harness")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private struct VietnameseTelexEssayHarnessView: View {
    let locale: AppLocale
    @State private var essayAnswer: AnswerValue?
    @State private var committedEmissions: Int = 0
    @State private var activeInputMode: String = ""
    @State private var controller = EssayEditorController()

    private let item = QuestionItem.essay(
        id: "harness-telex-essay",
        displayNo: 9,
        points: 5,
        stem: "<p>Viết đoạn văn ngắn bằng tiếng Việt có dấu.</p>",
        wordLimit: nil
    )

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text(essayAnswer?.essayHtml ?? "")
                        .font(.caption.monospaced())
                        .accessibilityIdentifier("harness.question.essayEcho")

                    Text("EMITS:\(committedEmissions)|IM:\(activeInputMode)")
                        .font(.caption.monospaced())
                        .accessibilityIdentifier("harness.question.essayEmitCount")

                    QuestionView(
                        item: item,
                        answer: essayAnswer,
                        mode: .answering,
                        essayController: controller,
                        onAnswer: { _, next in
                            committedEmissions += 1
                            essayAnswer = next
                        },
                        locale: locale
                    )
                }
                .padding(16)
            }
            .onReceive(NotificationCenter.default.publisher(for: UITextInputMode.currentInputModeDidChangeNotification)) { _ in
                activeInputMode = controller.textView?.textInputMode?.primaryLanguage ?? ""
            }
            .onReceive(NotificationCenter.default.publisher(for: UITextView.textDidBeginEditingNotification)) { _ in
                activeInputMode = controller.textView?.textInputMode?.primaryLanguage ?? ""
            }
            .navigationTitle("Vietnamese Telex Harness")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
#endif
