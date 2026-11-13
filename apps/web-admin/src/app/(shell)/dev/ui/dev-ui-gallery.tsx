"use client";

import {
  Badge,
  Breadcrumb,
  Button,
  Card,
  Checkbox,
  Chip,
  ConfirmDialog,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
  Drawer,
  DrawerContent,
  DrawerDescription,
  DrawerHeader,
  DrawerTitle,
  DrawerTrigger,
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
  EmptyState,
  FormField,
  IconButton,
  Input,
  Label,
  PageHeader,
  Pagination,
  SectionTitle,
  Select,
  Skeleton,
  StatusChip,
  Switch,
  Table,
  type TableProps,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  Textarea,
  notify,
  Tooltip,
} from "@thi360/ui";
import { Eye, Inbox, MoreHorizontal, Pencil, Plus, Search, Trash2 } from "lucide-react";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";

interface SampleAccount {
  id: string;
  code: string;
  username: string;
  nameKey: "content" | "ops" | "student3" | "student4";
  roleKey: "content" | "ops" | "student";
  status: "success" | "warning" | "danger" | "neutral";
  statusKey: "active" | "pending" | "locked" | "inactive";
}

const SAMPLE_ROWS: SampleAccount[] = [
  { id: "1", code: "U000001", username: "content.admin", nameKey: "content", roleKey: "content", status: "success", statusKey: "active" },
  { id: "2", code: "U000002", username: "ops.admin", nameKey: "ops", roleKey: "ops", status: "success", statusKey: "active" },
  { id: "3", code: "U000005", username: "student03", nameKey: "student3", roleKey: "student", status: "warning", statusKey: "pending" },
  { id: "4", code: "U000006", username: "student04", nameKey: "student4", roleKey: "student", status: "danger", statusKey: "locked" },
];

type TableState = "data" | "loading" | "empty" | "error";

export function DevUiGallery() {
  const t = useTranslations("devUi");
  const [theme, setTheme] = useState<"light" | "dark">("light");
  const [chipSelected, setChipSelected] = useState(true);
  const [checked, setChecked] = useState(true);
  const [switched, setSwitched] = useState(true);
  const [roleValue, setRoleValue] = useState("ops");
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [tableState, setTableState] = useState<TableState>("data");
  const [sorting, setSorting] = useState<{ id: string; desc: boolean }[]>([]);
  const [selection, setSelection] = useState<Record<string, boolean>>({ "2": true });
  const [page, setPage] = useState(2);
  const [pageSize, setPageSize] = useState(20);

  useEffect(() => {
    const previousTheme = document.documentElement.dataset.theme;
    return () => {
      if (previousTheme === undefined) {
        delete document.documentElement.dataset.theme;
      } else {
        document.documentElement.dataset.theme = previousTheme;
      }
    };
  }, []);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  const columns: TableProps<SampleAccount>["columns"] = [
    { accessorKey: "code", header: t("table.colCode") },
    { accessorKey: "username", header: t("table.colUsername") },
    {
      id: "fullName",
      header: t("table.colFullName"),
      accessorFn: (row) => t(`table.sampleNames.${row.nameKey}`),
      cell: ({ row }) => t(`table.sampleNames.${row.original.nameKey}`),
    },
    {
      id: "role",
      header: t("table.colRole"),
      enableSorting: false,
      cell: ({ row }) => t(`table.sampleRoles.${row.original.roleKey}`),
    },
    {
      id: "status",
      header: t("table.colStatus"),
      enableSorting: false,
      cell: ({ row }) => <StatusChip tone={row.original.status}>{t(`status.${row.original.statusKey}`)}</StatusChip>,
    },
    {
      id: "actions",
      header: () => <span className="block text-right">{t("table.colActions")}</span>,
      enableSorting: false,
      cell: ({ row }) => (
        <div className="flex justify-end gap-0.5">
          <IconButton aria-label={t("table.viewAction", { name: row.original.username })} icon={<Eye />} />
          <IconButton aria-label={t("table.editAction", { name: row.original.username })} icon={<Pencil />} />
          <IconButton
            tone="danger"
            aria-label={t("table.deleteAction", { name: row.original.username })}
            icon={<Trash2 />}
            disabled={row.original.id === "1"}
            tooltip={row.original.id === "1" ? t("table.deleteBlockedReason") : undefined}
          />
        </div>
      ),
    },
  ];

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title={t("title")}
        description={t("description")}
        actions={
          <Tabs
            variant="segmented"
            value={theme}
            onValueChange={(next) => setTheme(next === "dark" ? "dark" : "light")}
          >
            <TabsList aria-label={t("themeLabel")}>
              <TabsTrigger value="light">{t("themeLight")}</TabsTrigger>
              <TabsTrigger value="dark">{t("themeDark")}</TabsTrigger>
            </TabsList>
          </Tabs>
        }
      />

      <Card className="flex flex-col gap-4">
        <SectionTitle title={t("sections.buttons")} count="7" />
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="primary">
            <Plus />
            {t("buttons.primary")}
          </Button>
          <Button variant="secondary">{t("buttons.secondary")}</Button>
          <Button variant="outline">{t("buttons.outline")}</Button>
          <Button variant="soft">{t("buttons.soft")}</Button>
          <Button variant="exam">{t("buttons.exam")}</Button>
          <Button variant="danger">{t("buttons.danger")}</Button>
          <Button variant="link">{t("buttons.link")}</Button>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <Button size="sm">{t("buttons.sizeSm")}</Button>
          <Button size="md">{t("buttons.sizeMd")}</Button>
          <Button size="lg">{t("buttons.sizeLg")}</Button>
          <Button size="xl">{t("buttons.sizeXl")}</Button>
          <Button loading>{t("buttons.loading")}</Button>
          <Button disabled>{t("buttons.disabled")}</Button>
        </div>
      </Card>

      <Card className="flex flex-col gap-4">
        <SectionTitle title={t("sections.fields")} />
        <div className="grid grid-cols-[repeat(auto-fit,minmax(15rem,1fr))] gap-4">
          <FormField label={t("fields.searchLabel")} hint={t("fields.searchHint")}>
            {(props) => <Input {...props} leadingIcon={<Search />} placeholder={t("fields.searchPlaceholder")} />}
          </FormField>
          <FormField label={t("fields.errorLabel")} required error={t("fields.errorMessage")}>
            {(props) => <Input {...props} defaultValue="ops.admin" />}
          </FormField>
          <FormField label={t("fields.roleLabel")}>
            {(props) => (
              <Select
                {...props}
                value={roleValue}
                onValueChange={setRoleValue}
                options={[
                  { value: "ops", label: t("fields.roleOps") },
                  { value: "content", label: t("fields.roleContent") },
                  { value: "student", label: t("fields.roleStudent"), disabled: true },
                ]}
              />
            )}
          </FormField>
          <FormField label={t("fields.noteLabel")}>
            {(props) => <Textarea {...props} rows={2} placeholder={t("fields.notePlaceholder")} />}
          </FormField>
        </div>
        <div className="flex flex-wrap items-center gap-6">
          <div className="flex items-center gap-2">
            <Checkbox id="dev-cb" checked={checked} onCheckedChange={(v) => setChecked(v === true)} />
            <Label htmlFor="dev-cb">{t("fields.checkboxLabel")}</Label>
          </div>
          <div className="flex items-center gap-2">
            <Switch id="dev-sw" checked={switched} onCheckedChange={setSwitched} />
            <Label htmlFor="dev-sw">{t("fields.switchLabel")}</Label>
          </div>
        </div>
      </Card>

      <Card className="flex flex-col gap-4">
        <SectionTitle title={t("sections.chips")} count="5" />
        <div className="flex flex-wrap items-center gap-2.5">
          <StatusChip tone="success">{t("status.active")}</StatusChip>
          <StatusChip tone="warning">{t("status.pending")}</StatusChip>
          <StatusChip tone="danger">{t("status.locked")}</StatusChip>
          <StatusChip tone="info">{t("status.system")}</StatusChip>
          <StatusChip tone="neutral">{t("status.inactive")}</StatusChip>
        </div>
        <div className="flex flex-wrap items-center gap-2.5">
          <Chip selected={chipSelected} dotClassName="bg-primary-600" onClick={() => setChipSelected((v) => !v)}>
            {t("chips.filterSelected")}
          </Chip>
          <Chip selected={false} dotClassName="bg-warning">
            {t("chips.filterIdle")}
          </Chip>
          <Chip variant="tag">{t("chips.tag")}</Chip>
          <Badge>{t("chips.badge")}</Badge>
        </div>
      </Card>

      <Card className="flex flex-col gap-4">
        <SectionTitle title={t("sections.navigation")} />
        <Breadcrumb
          aria-label={t("navigation.breadcrumbAria")}
          items={[
            { label: t("navigation.crumbRoot"), href: "#" },
            { label: t("navigation.crumbSection"), href: "#" },
            { label: t("navigation.crumbCurrent") },
          ]}
        />
        <Tabs variant="content" defaultValue="roles">
          <TabsList>
            <TabsTrigger value="roles">{t("navigation.tabRoles")}</TabsTrigger>
            <TabsTrigger value="permissions">{t("navigation.tabPermissions")}</TabsTrigger>
            <TabsTrigger value="bulk">{t("navigation.tabBulk")}</TabsTrigger>
          </TabsList>
          <TabsContent value="roles" className="pt-3 text-text-secondary">
            {t("navigation.tabRolesBody")}
          </TabsContent>
          <TabsContent value="permissions" className="pt-3 text-text-secondary">
            {t("navigation.tabPermissionsBody")}
          </TabsContent>
          <TabsContent value="bulk" className="pt-3 text-text-secondary">
            {t("navigation.tabBulkBody")}
          </TabsContent>
        </Tabs>
      </Card>

      <Card className="flex flex-col gap-4">
        <SectionTitle title={t("sections.overlays")} />
        <div className="flex flex-wrap items-center gap-3">
          <Dialog>
            <DialogTrigger asChild>
              <Button variant="secondary">{t("overlays.openDialog")}</Button>
            </DialogTrigger>
            <DialogContent closeLabel={t("overlays.close")}>
              <DialogHeader>
                <DialogTitle>{t("overlays.dialogTitle")}</DialogTitle>
                <DialogDescription>{t("overlays.dialogDescription")}</DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="primary">{t("overlays.save")}</Button>
              </DialogFooter>
            </DialogContent>
          </Dialog>

          <Button variant="danger" onClick={() => setConfirmOpen(true)}>
            {t("overlays.openConfirm")}
          </Button>
          <ConfirmDialog
            open={confirmOpen}
            onOpenChange={setConfirmOpen}
            title={t("overlays.confirmTitle")}
            description={t("overlays.confirmDescription")}
            reasonLabel={t("overlays.reasonLabel")}
            requireReason
            confirmLabel={t("overlays.confirmAction")}
            cancelLabel={t("overlays.cancel")}
            closeLabel={t("overlays.close")}
            onConfirm={() => setConfirmOpen(false)}
          />

          <Drawer>
            <DrawerTrigger asChild>
              <Button variant="secondary">{t("overlays.openDrawer")}</Button>
            </DrawerTrigger>
            <DrawerContent closeLabel={t("overlays.close")}>
              <DrawerHeader>
                <DrawerTitle>{t("overlays.drawerTitle")}</DrawerTitle>
                <DrawerDescription>{t("overlays.drawerDescription")}</DrawerDescription>
              </DrawerHeader>
            </DrawerContent>
          </Drawer>

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="secondary">
                <MoreHorizontal />
                {t("overlays.openMenu")}
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              <DropdownMenuLabel>{t("overlays.menuHeading")}</DropdownMenuLabel>
              <DropdownMenuItem>{t("overlays.menuEdit")}</DropdownMenuItem>
              <DropdownMenuSeparator />
              <DropdownMenuItem destructive>{t("overlays.menuDelete")}</DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>

          <Tooltip content={t("overlays.tooltipContent")}>
            <Button variant="outline">{t("overlays.tooltipTrigger")}</Button>
          </Tooltip>

          <Button variant="soft" onClick={() => notify.success(t("overlays.toastSaved"))}>
            {t("overlays.triggerToast")}
          </Button>
        </div>
      </Card>

      <Card padding="none" className="flex flex-col">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border p-4">
          <SectionTitle title={t("sections.table")} count={String(SAMPLE_ROWS.length)} />
          <Tabs
            variant="content"
            value={tableState}
            onValueChange={(next) => setTableState(next as TableState)}
          >
            <TabsList aria-label={t("table.stateLabel")}>
              <TabsTrigger value="data">{t("table.stateData")}</TabsTrigger>
              <TabsTrigger value="loading">{t("table.stateLoading")}</TabsTrigger>
              <TabsTrigger value="empty">{t("table.stateEmpty")}</TabsTrigger>
              <TabsTrigger value="error">{t("table.stateError")}</TabsTrigger>
            </TabsList>
          </Tabs>
        </div>
        <Table
          aria-label={t("table.ariaLabel")}
          columns={columns}
          data={tableState === "empty" ? [] : SAMPLE_ROWS}
          getRowId={(row) => row.id}
          loading={tableState === "loading"}
          error={
            tableState === "error" ? (
              <EmptyState title={t("table.errorTitle")} description={t("table.errorDescription")} />
            ) : undefined
          }
          empty={<EmptyState icon={<Inbox />} title={t("table.emptyTitle")} description={t("table.emptyDescription")} />}
          sorting={{ state: sorting, onChange: setSorting }}
          selection={{
            state: selection,
            onChange: setSelection,
            selectAllLabel: t("table.selectAll"),
            rowLabel: (_index, row) => t("table.selectRow", { name: row.username }),
          }}
        />
        <div className="border-t border-border p-4">
          <Pagination
            page={page}
            pageSize={pageSize}
            total={84}
            onPageChange={setPage}
            pageSizeOptions={[10, 20, 50]}
            onPageSizeChange={setPageSize}
            labels={{
              nav: t("pagination.nav"),
              previous: t("pagination.previous"),
              next: t("pagination.next"),
              rowsPerPage: t("pagination.rowsPerPage"),
              page: (p) => t("pagination.page", { page: p }),
              range: ({ from, to, total }) => t("pagination.range", { from, to, total }),
            }}
          />
        </div>
      </Card>

      <Card className="flex flex-col gap-3">
        <SectionTitle title={t("sections.skeleton")} />
        <Skeleton className="h-5 w-48" />
        <Skeleton className="h-10 w-full" />
      </Card>
    </div>
  );
}
