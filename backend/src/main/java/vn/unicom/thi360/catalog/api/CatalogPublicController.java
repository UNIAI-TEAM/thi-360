package vn.unicom.thi360.catalog.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.unicom.thi360.catalog.application.CatalogQueryService;

@Tag(name = "Catalog Options", description = "Tra cứu danh mục đang hoạt động cho các module khác")
@RestController
@RequestMapping("/api/v1/catalog")
public class CatalogPublicController {

    private final CatalogQueryService catalogQueryService;

    public CatalogPublicController(CatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    @GetMapping("/options")
    @Operation(summary = "Danh sách giá trị đang hoạt động của một nhóm đang hoạt động")
    public List<CatalogOptionResponse> getOptions(@RequestParam String group) {
        return catalogQueryService.getOptions(group);
    }

    @GetMapping("/items/{id}")
    @Operation(summary = "Chi tiết một giá trị danh mục (bao gồm giá trị đã ngừng)")
    public CatalogItemResponse getItem(@PathVariable UUID id) {
        return catalogQueryService.getPublicItem(id);
    }

    @GetMapping("/tree")
    @Operation(summary = "Danh sách nút con trực tiếp đang có hiệu lực của cây danh mục")
    public List<CatalogTreeNodeResponse> getTree(
            @RequestParam String group,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(name = "ref", required = false) List<UUID> ref) {
        return catalogQueryService.getPublicTree(group, parentId, ref);
    }

    @GetMapping("/tree/descendants")
    @Operation(summary = "Danh sách nút con cháu hoặc nút lá đang có hiệu lực dưới một nút hoặc theo bộ lọc liên kết")
    public List<CatalogDescendantNodeResponse> getDescendants(
            @RequestParam(required = false) UUID nodeId,
            @RequestParam(required = false) String group,
            @RequestParam(defaultValue = "true") boolean leafOnly,
            @RequestParam(name = "ref", required = false) List<UUID> ref) {
        return catalogQueryService.getPublicDescendants(nodeId, group, leafOnly, ref);
    }
}
