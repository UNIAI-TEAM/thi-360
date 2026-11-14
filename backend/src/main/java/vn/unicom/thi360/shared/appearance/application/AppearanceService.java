package vn.unicom.thi360.shared.appearance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.unicom.thi360.shared.api.FieldError;
import vn.unicom.thi360.shared.api.ValidationException;
import vn.unicom.thi360.shared.api.VersionConflictException;
import vn.unicom.thi360.shared.appearance.api.AppearanceCustomColorsDto;
import vn.unicom.thi360.shared.appearance.api.AppearancePaletteDto;
import vn.unicom.thi360.shared.appearance.api.AppearanceResponse;
import vn.unicom.thi360.shared.appearance.api.ResetAppearanceRequest;
import vn.unicom.thi360.shared.appearance.api.UpdateAppearanceRequest;
import vn.unicom.thi360.shared.appearance.domain.AppearancePolicy;
import vn.unicom.thi360.shared.appearance.domain.AppearanceSettingPayload;
import vn.unicom.thi360.shared.appearance.domain.AppearanceSettingPayload.CustomColors;
import vn.unicom.thi360.shared.appearance.domain.AppearanceSettingPayload.Palette;
import vn.unicom.thi360.shared.security.CurrentUser;
import vn.unicom.thi360.shared.security.CurrentUserProvider;
import vn.unicom.thi360.shared.web.LocaleConfig;

@Service
public class AppearanceService {

    private static final Logger log = LoggerFactory.getLogger(AppearanceService.class);
    private static final String SELECT_SETTING_SQL =
            "SELECT value::text, version FROM shared.system_setting WHERE key = ?";
    private static final String INSERT_SETTING_SQL =
            """
            INSERT INTO shared.system_setting (key, value, version, updated_at, updated_by)
            VALUES (?, cast(? as jsonb), 1, ?, ?)
            ON CONFLICT (key) DO NOTHING
            """;
    private static final String UPDATE_SETTING_SQL =
            """
            UPDATE shared.system_setting
            SET value = cast(? as jsonb), version = version + 1, updated_at = ?, updated_by = ?
            WHERE key = ? AND version = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final CurrentUserProvider currentUserProvider;
    private final MessageSource messageSource;
    private final AppearanceAuditRecorder auditRecorder;

    public AppearanceService(
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            Clock clock,
            CurrentUserProvider currentUserProvider,
            MessageSource messageSource,
            @Autowired(required = false) AppearanceAuditRecorder auditRecorder) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.currentUserProvider = Objects.requireNonNull(currentUserProvider, "currentUserProvider must not be null");
        this.messageSource = Objects.requireNonNull(messageSource, "messageSource must not be null");
        this.auditRecorder = auditRecorder;
    }

    @Transactional(readOnly = true)
    public AppearanceResponse getAppearance() {
        SettingRow row = loadSettingRow();
        if (row == null) {
            return toResponse(AppearanceSettingPayload.defaultConfig(), 0L);
        }
        return toResponse(deserializePayload(row.valueJson()), row.version());
    }

    @Transactional
    public AppearanceResponse updateAppearance(UpdateAppearanceRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        validateUpdateRequest(request);

        CustomColors requestedCustom = request.palette().custom() != null
                ? new CustomColors(request.palette().custom().primary(), request.palette().custom().accent())
                : null;
        boolean preserveStoredCustom = !AppearancePolicy.isCustomPreset(request.palette().presetId());

        AppearanceResponse updated = applyChange(
                actor,
                request.palette().presetId(),
                requestedCustom,
                request.mode(),
                preserveStoredCustom,
                request.version());
        log.atInfo()
                .addKeyValue("event", "appearance.updated")
                .addKeyValue("presetId", updated.palette().presetId())
                .addKeyValue("mode", updated.mode())
                .addKeyValue("version", updated.version())
                .log("Updated global appearance setting");
        return updated;
    }

    @Transactional
    public AppearanceResponse resetAppearance(ResetAppearanceRequest request) {
        CurrentUser actor = currentUserProvider.requireCurrentUser();
        if (request == null || request.version() == null) {
            throw new ValidationException(List.of(new FieldError(
                    "version",
                    "APPEARANCE_VERSION_REQUIRED",
                    resolveMessage("APPEARANCE_VERSION_REQUIRED"))));
        }
        if (request.version() < 0L) {
            throw new ValidationException(List.of(new FieldError(
                    "version",
                    "APPEARANCE_VERSION_INVALID",
                    resolveMessage("APPEARANCE_VERSION_INVALID"))));
        }

        AppearanceResponse updated = applyChange(
                actor,
                AppearancePolicy.DEFAULT_PRESET_ID,
                null,
                AppearancePolicy.DEFAULT_MODE,
                false,
                request.version());
        log.atInfo()
                .addKeyValue("event", "appearance.reset")
                .addKeyValue("presetId", updated.palette().presetId())
                .addKeyValue("mode", updated.mode())
                .addKeyValue("version", updated.version())
                .log("Reset global appearance setting to defaults");
        return updated;
    }

    private AppearanceResponse applyChange(
            CurrentUser actor,
            String nextPresetId,
            CustomColors requestedCustom,
            String nextMode,
            boolean preserveStoredCustom,
            long expectedVersion) {
        SettingRow existing = loadSettingRow();
        long currentVersion = existing == null ? 0L : existing.version();
        if (expectedVersion != currentVersion) {
            throw new VersionConflictException();
        }

        AppearanceSettingPayload beforePayload = existing == null
                ? AppearanceSettingPayload.defaultConfig()
                : deserializePayload(existing.valueJson());

        CustomColors nextCustom = requestedCustom != null
                ? requestedCustom
                : (preserveStoredCustom ? beforePayload.palette().custom() : null);
        AppearanceSettingPayload nextPayload = new AppearanceSettingPayload(
                new Palette(nextPresetId, nextCustom),
                nextMode);

        String nextJson = serializePayload(nextPayload);
        Instant now = Instant.now(clock);
        OffsetDateTime updatedAt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
        long newVersion;

        if (existing == null) {
            int inserted = jdbcTemplate.update(
                    INSERT_SETTING_SQL,
                    AppearancePolicy.SETTING_KEY,
                    nextJson,
                    updatedAt,
                    actor.id());
            if (inserted == 0) {
                throw new VersionConflictException();
            }
            newVersion = 1L;
        } else {
            int updatedRows = jdbcTemplate.update(
                    UPDATE_SETTING_SQL,
                    nextJson,
                    updatedAt,
                    actor.id(),
                    AppearancePolicy.SETTING_KEY,
                    currentVersion);
            if (updatedRows == 0) {
                throw new VersionConflictException();
            }
            newVersion = currentVersion + 1L;
        }

        if (auditRecorder != null) {
            auditRecorder.recordUpdated(
                    toAuditState(beforePayload, currentVersion),
                    toAuditState(nextPayload, newVersion));
        }

        return toResponse(nextPayload, newVersion);
    }

    private void validateUpdateRequest(UpdateAppearanceRequest request) {
        List<FieldError> errors = new ArrayList<>();
        if (request == null) {
            errors.add(new FieldError(
                    "body",
                    "VALIDATION_FAILED",
                    resolveMessage("VALIDATION_FAILED")));
            throw new ValidationException(errors);
        }

        if (!AppearancePolicy.isValidMode(request.mode())) {
            errors.add(new FieldError(
                    "mode",
                    "APPEARANCE_MODE_INVALID",
                    resolveMessage("APPEARANCE_MODE_INVALID")));
        }

        AppearancePaletteDto palette = request.palette();
        if (palette == null) {
            errors.add(new FieldError(
                    "palette",
                    "APPEARANCE_PALETTE_REQUIRED",
                    resolveMessage("APPEARANCE_PALETTE_REQUIRED")));
        } else {
            if (!AppearancePolicy.isValidPresetOrCustom(palette.presetId())) {
                errors.add(new FieldError(
                        "palette.presetId",
                        "APPEARANCE_PRESET_UNKNOWN",
                        resolveMessage("APPEARANCE_PRESET_UNKNOWN")));
            }
            AppearanceCustomColorsDto custom = palette.custom();
            if (AppearancePolicy.isCustomPreset(palette.presetId()) && custom == null) {
                errors.add(new FieldError(
                        "palette.custom",
                        "APPEARANCE_CUSTOM_REQUIRED",
                        resolveMessage("APPEARANCE_CUSTOM_REQUIRED")));
            }
            if (custom != null) {
                if (!AppearancePolicy.isValidHexColor(custom.primary())) {
                    errors.add(new FieldError(
                            "palette.custom.primary",
                            "APPEARANCE_HEX_INVALID",
                            resolveMessage("APPEARANCE_HEX_INVALID")));
                }
                if (!AppearancePolicy.isValidHexColor(custom.accent())) {
                    errors.add(new FieldError(
                            "palette.custom.accent",
                            "APPEARANCE_HEX_INVALID",
                            resolveMessage("APPEARANCE_HEX_INVALID")));
                }
            }
        }

        if (request.version() == null) {
            errors.add(new FieldError(
                    "version",
                    "APPEARANCE_VERSION_REQUIRED",
                    resolveMessage("APPEARANCE_VERSION_REQUIRED")));
        } else if (request.version() < 0L) {
            errors.add(new FieldError(
                    "version",
                    "APPEARANCE_VERSION_INVALID",
                    resolveMessage("APPEARANCE_VERSION_INVALID")));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private SettingRow loadSettingRow() {
        List<SettingRow> rows = jdbcTemplate.query(
                SELECT_SETTING_SQL,
                (rs, rowNum) -> new SettingRow(rs.getString(1), rs.getLong(2)),
                AppearancePolicy.SETTING_KEY);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private AppearanceSettingPayload deserializePayload(String json) {
        try {
            return objectMapper.readValue(json, AppearanceSettingPayload.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize appearance setting payload", ex);
        }
    }

    private String serializePayload(AppearanceSettingPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize appearance setting payload", ex);
        }
    }

    private String resolveMessage(String code) {
        Locale locale = LocaleConfig.currentLocale();
        String resolved = messageSource.getMessage(code, null, null, locale);
        return (resolved != null && !resolved.isBlank()) ? resolved : code;
    }

    private static AppearanceResponse toResponse(AppearanceSettingPayload payload, long version) {
        Palette palette = payload.palette();
        AppearanceCustomColorsDto customDto = palette.custom() != null
                ? new AppearanceCustomColorsDto(palette.custom().primary(), palette.custom().accent())
                : null;
        return new AppearanceResponse(
                new AppearancePaletteDto(palette.presetId(), customDto),
                payload.mode(),
                version);
    }

    private static Map<String, Object> toAuditState(AppearanceSettingPayload payload, long version) {
        Map<String, Object> paletteMap = new LinkedHashMap<>();
        paletteMap.put("presetId", payload.palette().presetId());
        if (payload.palette().custom() != null) {
            Map<String, Object> customMap = new LinkedHashMap<>();
            customMap.put("primary", payload.palette().custom().primary());
            customMap.put("accent", payload.palette().custom().accent());
            paletteMap.put("custom", customMap);
        }

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("palette", paletteMap);
        state.put("mode", payload.mode());
        state.put("version", version);
        return state;
    }

    private record SettingRow(String valueJson, long version) {
    }
}
