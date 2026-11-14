package vn.unicom.thi360.identity.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import vn.unicom.thi360.identity.application.ImportExcelSchemaHelper.ImportColumn;
import vn.unicom.thi360.identity.application.ImportWorkbookCodec.ParsedImportRow;
import vn.unicom.thi360.identity.domain.ImportRow;
import vn.unicom.thi360.identity.domain.ImportRowError;
import vn.unicom.thi360.identity.domain.Normalizer;
import vn.unicom.thi360.identity.domain.PasswordPolicy;
import vn.unicom.thi360.identity.domain.UsernamePolicy;
import vn.unicom.thi360.identity.infrastructure.UserAccountRepository;
import vn.unicom.thi360.identity.spi.ClassMembershipPort;
import vn.unicom.thi360.identity.spi.ClassMembershipPort.ClassLookupInfo;

@Component
public class ImportRowValidationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private static final int MAX_FULL_NAME_LENGTH = 200;
    private static final int MAX_EMAIL_LENGTH = 255;
    private static final int MAX_PHONE_LENGTH = 30;

    private final UsernamePolicy usernamePolicy;
    private final PasswordPolicy passwordPolicy;
    private final UserAccountRepository userAccountRepository;
    private final ClassMembershipPort classMembershipPort;
    private final ImportExcelSchemaHelper schemaHelper;

    public ImportRowValidationService(
            UsernamePolicy usernamePolicy,
            PasswordPolicy passwordPolicy,
            UserAccountRepository userAccountRepository,
            ClassMembershipPort classMembershipPort,
            ImportExcelSchemaHelper schemaHelper) {
        this.usernamePolicy = usernamePolicy;
        this.passwordPolicy = passwordPolicy;
        this.userAccountRepository = userAccountRepository;
        this.classMembershipPort = classMembershipPort;
        this.schemaHelper = schemaHelper;
    }

    public List<ValidatedRow> validateParsedRows(List<ParsedImportRow> parsedRows, Locale locale) {
        if (parsedRows == null || parsedRows.isEmpty()) {
            return List.of();
        }

        Set<String> candidateUsernames = new LinkedHashSet<>();
        Set<String> candidateEmails = new LinkedHashSet<>();
        Set<String> candidateClassCodes = new LinkedHashSet<>();
        for (ParsedImportRow row : parsedRows) {
            String un = Normalizer.normalize(row.username());
            if (un != null) {
                candidateUsernames.add(un);
            }
            String em = Normalizer.normalize(row.email());
            if (em != null) {
                candidateEmails.add(em);
            }
            String cc = Normalizer.normalize(row.classCode());
            if (cc != null) {
                candidateClassCodes.add(cc);
            }
        }

        Set<String> takenUsernames = candidateUsernames.isEmpty()
                ? Set.of()
                : userAccountRepository.findActiveUsernamesNormalizedIn(candidateUsernames);
        Set<String> takenEmails = candidateEmails.isEmpty()
                ? Set.of()
                : userAccountRepository.findActiveEmailsNormalizedIn(candidateEmails);
        Map<String, ClassLookupInfo> classesByCode = candidateClassCodes.isEmpty()
                ? Map.of()
                : classMembershipPort.findClassesByNormalizedCodes(candidateClassCodes);

        Set<String> seenUsernames = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        List<ValidatedRow> results = new ArrayList<>(parsedRows.size());

        for (ParsedImportRow row : parsedRows) {
            List<ImportRowError> errors = new ArrayList<>();
            String usernameNorm = Normalizer.normalize(row.username());
            String emailNorm = Normalizer.normalize(row.email());
            String classCodeNorm = Normalizer.normalize(row.classCode());

            if (row.username() == null || row.username().isBlank()) {
                errors.add(error(row.rowNo(), ImportColumn.USERNAME, "REQUIRED", locale));
            } else {
                UsernamePolicy.CheckResult check = usernamePolicy.check(row.username());
                if (!check.valid()) {
                    errors.add(error(row.rowNo(), ImportColumn.USERNAME, "USERNAME_INVALID", locale));
                } else {
                    if (!seenUsernames.add(usernameNorm)) {
                        errors.add(error(row.rowNo(), ImportColumn.USERNAME, "USERNAME_DUPLICATE_IN_FILE", locale));
                    }
                    if (takenUsernames.contains(usernameNorm)) {
                        errors.add(error(row.rowNo(), ImportColumn.USERNAME, "USERNAME_TAKEN", locale));
                    }
                }
            }

            if (row.password() != null && !row.password().isBlank()) {
                if (!passwordPolicy.isValid(row.password(), row.username())) {
                    errors.add(error(row.rowNo(), ImportColumn.PASSWORD, "PASSWORD_WEAK", locale));
                }
            }

            if (row.fullName() == null || row.fullName().isBlank()) {
                errors.add(error(row.rowNo(), ImportColumn.FULL_NAME, "REQUIRED", locale));
            } else if (row.fullName().length() > MAX_FULL_NAME_LENGTH) {
                errors.add(error(row.rowNo(), ImportColumn.FULL_NAME, "FULL_NAME_TOO_LONG", locale));
            }

            if (row.email() != null && !row.email().isBlank()) {
                if (row.email().length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(row.email()).matches()) {
                    errors.add(error(row.rowNo(), ImportColumn.EMAIL, "EMAIL_INVALID", locale));
                } else {
                    if (!seenEmails.add(emailNorm)) {
                        errors.add(error(row.rowNo(), ImportColumn.EMAIL, "EMAIL_DUPLICATE_IN_FILE", locale));
                    }
                    if (takenEmails.contains(emailNorm)) {
                        errors.add(error(row.rowNo(), ImportColumn.EMAIL, "EMAIL_TAKEN", locale));
                    }
                }
            }

            if (row.phone() != null && row.phone().length() > MAX_PHONE_LENGTH) {
                errors.add(error(row.rowNo(), ImportColumn.PHONE, "PHONE_TOO_LONG", locale));
            }

            UUID resolvedClassId = null;
            if (classCodeNorm != null) {
                ClassLookupInfo info = classesByCode.get(classCodeNorm);
                if (info == null) {
                    errors.add(error(row.rowNo(), ImportColumn.CLASS_CODE, "CLASS_NOT_FOUND", locale));
                } else if (!info.active()) {
                    errors.add(error(row.rowNo(), ImportColumn.CLASS_CODE, "CLASS_INACTIVE", locale));
                } else {
                    resolvedClassId = info.id();
                }
            }

            results.add(new ValidatedRow(
                    row,
                    usernameNorm,
                    emailNorm,
                    classCodeNorm,
                    resolvedClassId,
                    List.copyOf(errors)));
        }
        return results;
    }

    public int revalidatePersistedRows(List<ImportRow> rows, Locale locale) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }

        Set<String> candidateUsernames = new LinkedHashSet<>();
        Set<String> candidateEmails = new LinkedHashSet<>();
        Set<String> candidateClassCodes = new LinkedHashSet<>();
        for (ImportRow row : rows) {
            if (row.getUsernameNormalized() != null) {
                candidateUsernames.add(row.getUsernameNormalized());
            }
            if (row.getEmailNormalized() != null) {
                candidateEmails.add(row.getEmailNormalized());
            }
            if (row.getClassCodeNormalized() != null) {
                candidateClassCodes.add(row.getClassCodeNormalized());
            }
        }

        Set<String> takenUsernames = candidateUsernames.isEmpty()
                ? Set.of()
                : userAccountRepository.findActiveUsernamesNormalizedIn(candidateUsernames);
        Set<String> takenEmails = candidateEmails.isEmpty()
                ? Set.of()
                : userAccountRepository.findActiveEmailsNormalizedIn(candidateEmails);
        Map<String, ClassLookupInfo> classesByCode = candidateClassCodes.isEmpty()
                ? Map.of()
                : classMembershipPort.findClassesByNormalizedCodes(candidateClassCodes);

        Set<String> seenUsernames = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();
        int invalidCount = 0;

        for (ImportRow row : rows) {
            List<ImportRowError> errors = new ArrayList<>();
            String rawUsername = row.getRaw().username();
            String rawFullName = row.getRaw().fullName();
            String rawEmail = row.getRaw().email();
            String rawPhone = row.getRaw().phone();
            String usernameNorm = row.getUsernameNormalized();
            String emailNorm = row.getEmailNormalized();
            String classCodeNorm = row.getClassCodeNormalized();

            if (rawUsername == null || rawUsername.isBlank()) {
                errors.add(error(row.getRowNo(), ImportColumn.USERNAME, "REQUIRED", locale));
            } else {
                UsernamePolicy.CheckResult check = usernamePolicy.check(rawUsername);
                if (!check.valid()) {
                    errors.add(error(row.getRowNo(), ImportColumn.USERNAME, "USERNAME_INVALID", locale));
                } else {
                    if (!seenUsernames.add(usernameNorm)) {
                        errors.add(error(row.getRowNo(), ImportColumn.USERNAME, "USERNAME_DUPLICATE_IN_FILE", locale));
                    }
                    if (takenUsernames.contains(usernameNorm)) {
                        errors.add(error(row.getRowNo(), ImportColumn.USERNAME, "USERNAME_TAKEN", locale));
                    }
                }
            }

            if (rawFullName == null || rawFullName.isBlank()) {
                errors.add(error(row.getRowNo(), ImportColumn.FULL_NAME, "REQUIRED", locale));
            } else if (rawFullName.length() > MAX_FULL_NAME_LENGTH) {
                errors.add(error(row.getRowNo(), ImportColumn.FULL_NAME, "FULL_NAME_TOO_LONG", locale));
            }

            if (rawEmail != null && !rawEmail.isBlank()) {
                if (rawEmail.length() > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.matcher(rawEmail).matches()) {
                    errors.add(error(row.getRowNo(), ImportColumn.EMAIL, "EMAIL_INVALID", locale));
                } else {
                    if (!seenEmails.add(emailNorm)) {
                        errors.add(error(row.getRowNo(), ImportColumn.EMAIL, "EMAIL_DUPLICATE_IN_FILE", locale));
                    }
                    if (takenEmails.contains(emailNorm)) {
                        errors.add(error(row.getRowNo(), ImportColumn.EMAIL, "EMAIL_TAKEN", locale));
                    }
                }
            }

            if (rawPhone != null && rawPhone.length() > MAX_PHONE_LENGTH) {
                errors.add(error(row.getRowNo(), ImportColumn.PHONE, "PHONE_TOO_LONG", locale));
            }

            UUID resolvedClassId = null;
            if (classCodeNorm != null) {
                ClassLookupInfo info = classesByCode.get(classCodeNorm);
                if (info == null) {
                    errors.add(error(row.getRowNo(), ImportColumn.CLASS_CODE, "CLASS_NOT_FOUND", locale));
                } else if (!info.active()) {
                    errors.add(error(row.getRowNo(), ImportColumn.CLASS_CODE, "CLASS_INACTIVE", locale));
                } else {
                    resolvedClassId = info.id();
                }
            }

            row.updateValidation(resolvedClassId, errors);
            if (!errors.isEmpty()) {
                invalidCount++;
            }
        }
        return invalidCount;
    }

    private ImportRowError error(int rowNo, ImportColumn column, String code, Locale locale) {
        String colLabel = schemaHelper.columnLabel(column, locale);
        String msg = schemaHelper.rowErrorMessage(code, colLabel, locale);
        return new ImportRowError(rowNo, colLabel, code, msg);
    }

    public record ValidatedRow(
            ParsedImportRow parsed,
            String usernameNormalized,
            String emailNormalized,
            String classCodeNormalized,
            UUID resolvedClassId,
            List<ImportRowError> errors
    ) {
    }
}
