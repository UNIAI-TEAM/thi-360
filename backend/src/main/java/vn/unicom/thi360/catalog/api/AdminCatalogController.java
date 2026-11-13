package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.unicom.thi360.catalog.application.CatalogAdminService;
import vn.unicom.thi360.catalog.application.CatalogExcelService;
import vn.unicom.thi360.catalog.spi.CatalogGroupKind;
import vn.unicom.thi360.catalog.spi.CatalogGroupType;
import vn.unicom.thi360.catalog.spi.CatalogStatus;
import vn.unicom.thi360.identity.spi.Permissions;
import vn.unicom.thi360.shared.api.PageQuery;
import vn.unicom.thi360.shared.api.PageResponse;
import vn.unicom.thi360.shared.security.RequirePermission;

@Tag(name = "Admin Catalog", description = "Quản trị danh mục dùng chung, danh mục cây và liên kết")
@RestController
@RequestMapping("/api/v1/admin/catalog")
public class AdminCatalogController {

    private static final MediaType XLSX_MEDIA_TYPE = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CatalogAdminService catalogAdminService;
    private final CatalogExcelService catalogExcelService;

    public AdminCatalogController(
            CatalogAdminService catalogAdminService, CatalogExcelService catalogExcelService) {
        this.catalogAdminService = catalogAdminService;
        this.catalogExcelService = catalogExcelService;
    }

    @GetMapping("/groups")
    @RequirePermission(Permissions.CATALOG_VIEW)
    @Operation(summary = "Danh sách nhóm danh mục kèm số lượng giá trị")
    public PageResponse<CatalogGroupResponse> listGroups(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(required = false) CatalogGroupType type,
            @RequestParam(required = false) CatalogGroupKind kind,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        PageQuery pageQuery = PageQuery.of(
                page, size, sort, CatalogAdminService.ALLOWED_GROUP_SORT_FIELDS, Sort.unsorted());
        return catalogAdminService.listGroups(q, status, type, kind, pageQuery);
    }

    @GetMapping("/groups/{id}")
    @RequirePermission(Permissions.CATALOG_VIEW)
    @Operation(summary = "Chi tiết nhóm danh mục")
    public CatalogGroupResponse getGroup(@PathVariable UUID id) {
        return catalogAdminService.getGroup(id);
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Tạo nhóm danh mục tùy chỉnh mới")
    public CatalogGroupResponse createGroup(@Valid @RequestBody CreateCatalogGroupRequest request) {
        return catalogAdminService.createGroup(request);
    }

    @PatchMapping("/groups/{id}")
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Cập nhật nhóm danh mục tùy chỉnh")
    public CatalogGroupResponse updateGroup(
            @PathVariable UUID id, @Valid @RequestBody UpdateCatalogGroupRequest request) {
        return catalogAdminService.updateGroup(id, request);
    }

    @DeleteMapping("/groups/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Xóa nhóm danh mục tùy chỉnh chưa có giá trị")
    public void deleteGroup(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        catalogAdminService.deleteGroup(id, reason);
    }

    @GetMapping("/groups/{id}/items")
    @RequirePermission(Permissions.CATALOG_VIEW)
    @Operation(summary = "Danh sách giá trị trong nhóm danh mục")
    public PageResponse<CatalogItemResponse> listItems(
            @PathVariable UUID id,
            @RequestParam(required = false) String nameVi,
            @RequestParam(required = false) String nameEn,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        PageQuery pageQuery = PageQuery.of(
                page, size, sort, CatalogAdminService.ALLOWED_ITEM_SORT_FIELDS, Sort.unsorted());
        return catalogAdminService.listItems(id, nameVi, nameEn, status, q, pageQuery);
    }

    @PostMapping("/groups/{id}/items")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Tạo giá trị mới trong nhóm danh mục")
    public CatalogItemResponse createItem(
            @PathVariable UUID id, @Valid @RequestBody CreateCatalogItemRequest request) {
        return catalogAdminService.createItem(id, request);
    }

    @PutMapping("/groups/{id}/items/order")
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Sắp xếp thứ tự hiển thị giá trị trong nhóm hoặc cùng nút cha")
    public List<CatalogItemResponse> reorderItems(
            @PathVariable UUID id, @Valid @RequestBody ReorderCatalogItemsRequest request) {
        return catalogAdminService.reorderItems(id, request);
    }

    @GetMapping("/items/{id}")
    @RequirePermission(Permissions.CATALOG_VIEW)
    @Operation(summary = "Chi tiết giá trị danh mục")
    public CatalogItemResponse getItem(@PathVariable UUID id) {
        return catalogAdminService.getItem(id);
    }

    @PatchMapping("/items/{id}")
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Cập nhật giá trị danh mục")
    public CatalogItemResponse updateItem(
            @PathVariable UUID id, @Valid @RequestBody UpdateCatalogItemRequest request) {
        return catalogAdminService.updateItem(id, request);
    }

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Xóa giá trị danh mục")
    public void deleteItem(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        catalogAdminService.deleteItem(id, reason);
    }

    @GetMapping("/groups/{id}/tree")
    @RequirePermission(Permissions.CATALOG_VIEW)
    @Operation(summary = "Tra cứu cây danh mục theo nút cha hoặc từ khóa tìm kiếm")
    public List<CatalogTreeNodeResponse> listTree(
            @PathVariable UUID id,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Short depth,
            @RequestParam(required = false) CatalogStatus status,
            @RequestParam(name = "ref", required = false) List<UUID> ref) {
        return catalogAdminService.listAdminTree(id, parentId, q, depth, status, ref);
    }

    @PutMapping("/items/{id}/parent")
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Chuyển nút cây sang nút cha khác cùng cấp")
    public CatalogItemResponse moveItem(
            @PathVariable UUID id, @Valid @RequestBody MoveCatalogItemRequest request) {
        return catalogAdminService.moveItem(id, request);
    }

    @GetMapping("/groups/{id}/ref-groups")
    @RequirePermission(Permissions.CATALOG_VIEW)
    @Operation(summary = "Danh sách danh mục liên kết của nhóm cây kèm số nút đang liên kết")
    public List<CatalogGroupRefResponse> listRefGroups(@PathVariable UUID id) {
        return catalogAdminService.listGroupRefGroups(id);
    }

    @PutMapping("/groups/{id}/ref-groups/{refGroupId}")
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Thêm danh mục liên kết cho nhóm cây")
    public List<CatalogGroupRefResponse> addRefGroup(
            @PathVariable UUID id, @PathVariable UUID refGroupId) {
        return catalogAdminService.addGroupRefGroup(id, refGroupId);
    }

    @DeleteMapping("/groups/{id}/ref-groups/{refGroupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Bỏ danh mục liên kết không mặc định khỏi nhóm cây")
    public void removeRefGroup(@PathVariable UUID id, @PathVariable UUID refGroupId) {
        catalogAdminService.removeGroupRefGroup(id, refGroupId);
    }

    @PutMapping("/items/{id}/refs/{refItemId}")
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Gắn thêm một liên kết giá trị cho nút cây")
    public CatalogItemResponse addItemRef(@PathVariable UUID id, @PathVariable UUID refItemId) {
        return catalogAdminService.addItemRef(id, refItemId);
    }

    @DeleteMapping("/items/{id}/refs/{refItemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Gỡ một liên kết giá trị khỏi nút cây")
    public void removeItemRef(@PathVariable UUID id, @PathVariable UUID refItemId) {
        catalogAdminService.removeItemRef(id, refItemId);
    }

    @GetMapping("/items/{id}/versions")
    @RequirePermission(Permissions.CATALOG_VIEW)
    @Operation(summary = "Danh sách phiên bản của mã tổ hợp môn")
    public List<CombinationVersionResponse> listItemVersions(@PathVariable UUID id) {
        return catalogAdminService.listCombinationVersions(id);
    }

    @GetMapping("/combination-versions/{versionId}")
    @RequirePermission(Permissions.CATALOG_VIEW)
    @Operation(summary = "Chi tiết một phiên bản lịch sử hoặc hiện hành của mã tổ hợp")
    public CombinationVersionResponse getCombinationVersion(@PathVariable UUID versionId) {
        return catalogAdminService.getCombinationVersion(versionId);
    }

    @GetMapping("/items/{id}/usages")
    @RequirePermission(Permissions.CATALOG_VIEW)
    @Operation(summary = "Danh sách nơi đang sử dụng của giá trị (nhóm cây gộp cả nhánh)")
    public CatalogUsagesResponse getItemUsages(@PathVariable UUID id) {
        return catalogAdminService.getItemUsages(id);
    }

    @GetMapping("/groups/{id}/usages")
    @RequirePermission(Permissions.CATALOG_VIEW)
    @Operation(summary = "Danh sách nơi đang sử dụng của toàn nhóm danh mục")
    public CatalogUsagesResponse getGroupUsages(@PathVariable UUID id) {
        return catalogAdminService.getGroupUsages(id);
    }

    @GetMapping("/groups/{id}/items/export")
    @RequirePermission(Permissions.CATALOG_VIEW)
    @Operation(summary = "Xuất danh sách giá trị của nhóm danh mục ra tệp Excel")
    public ResponseEntity<byte[]> exportItems(@PathVariable UUID id) {
        CatalogExcelService.ExportedWorkbook exported = catalogExcelService.exportGroupItems(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        headers.setContentDisposition(ContentDisposition.attachment().filename(exported.filename()).build());
        return ResponseEntity.ok().headers(headers).body(exported.bytes());
    }

    @PostMapping(value = "/groups/{id}/items/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Tải lên tệp Excel để kiểm tra và xem trước trước khi nhập danh mục")
    public CatalogImportPreviewResponse previewImport(
            @PathVariable UUID id, @RequestParam("file") MultipartFile file) {
        return catalogExcelService.previewImport(id, file);
    }

    @PostMapping("/groups/{id}/items/import/{previewId}/confirm")
    @RequirePermission(Permissions.CATALOG_MODIFY)
    @Operation(summary = "Xác nhận nhập danh mục từ phiên xem trước (hỗ trợ Idempotency-Key)")
    public CatalogImportResultResponse confirmImport(
            @PathVariable UUID id,
            @PathVariable UUID previewId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return catalogExcelService.confirmImport(id, previewId, idempotencyKey);
    }
}
