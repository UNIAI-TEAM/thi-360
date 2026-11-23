"use client";

import { useQueryClient } from "@tanstack/react-query";
import { Button, Card, EmptyState, IconButton, Skeleton } from "@thi360/ui";
import { RefreshCw, TriangleAlert, Users, X } from "lucide-react";
import Link from "next/link";
import { useFormatter, useTranslations } from "next-intl";
import { useState } from "react";
import { usePermissions } from "@/features/session/session-provider";
import { EXAMS_QUERY_KEYS } from "../keys";
import { useExam } from "../queries";
import { ListingForm } from "./listing-form";
import { ListingPriceStrip } from "./listing-price-strip";
import { useExamListing } from "./queries";

export const LISTING_EDIT_PERMISSION = "EXAM.MODIFY";

export function ExamListingView({ examId }: { examId: string }) {
  const t = useTranslations("exams.listing");
  const format = useFormatter();
  const queryClient = useQueryClient();
  const examQuery = useExam(examId);
  const listingQuery = useExamListing(examId);
  const permissions = usePermissions();
  const [conflict, setConflict] = useState(false);

  if (examQuery.isPending || listingQuery.isPending) {
    return (
      <div className="@container flex flex-col gap-4" aria-busy="true" aria-label={t("loading")}>
        <Skeleton className="h-16" />
        <div className="grid grid-cols-1 gap-6 @5xl:grid-cols-[minmax(0,1fr)_22rem]">
          <Skeleton className="h-96 min-w-0" />
          <Skeleton className="h-96 min-w-0" />
        </div>
      </div>
    );
  }

  if (examQuery.isError || listingQuery.isError) {
    return (
      <Card>
        <EmptyState
          headingLevel={2}
          icon={<TriangleAlert />}
          title={t("errorTitle")}
          description={t("errorDescription")}
          action={
            <Button
              variant="secondary"
              onClick={() => {
                if (examQuery.isError) void examQuery.refetch();
                if (listingQuery.isError) void listingQuery.refetch();
              }}
            >
              <RefreshCw />
              {t("retry")}
            </Button>
          }
        />
      </Card>
    );
  }

  const exam = examQuery.data;
  const listing = listingQuery.data;
  const canEdit = !permissions.isLoading && permissions.can(LISTING_EDIT_PERMISSION);
  const canViewOrders = !permissions.isLoading && permissions.can("ORDER.VIEW");

  return (
    <div className="flex flex-col gap-4">
      {canViewOrders ? (
        <div className="flex justify-end">
          <Button asChild variant="outline" size="sm">
            <Link href={`/orders/buyers?exam=${encodeURIComponent(exam.id)}`}>
              <Users aria-hidden="true" />
              <span>{t("buyersLink")}</span>
            </Link>
          </Button>
        </div>
      ) : null}
      <ListingPriceStrip listing={listing} />
      {conflict && (
        <div
          role="alert"
          className="flex items-start gap-2.5 rounded-lg border border-danger/30 bg-danger-bg px-4 py-3"
        >
          <RefreshCw aria-hidden className="mt-0.5 size-5 shrink-0 text-danger" />
          <div className="flex min-w-0 flex-1 flex-col gap-0.5">
            <strong className="font-semibold text-danger">{t("conflict.title")}</strong>
            <span className="text-body text-text-secondary">
              {t("conflict.description")}
              {listing.updatedAt ? ` ${t("conflict.lastEdit", { time: format.dateTime(new Date(listing.updatedAt), "dateTime") })}` : ""}
            </span>
          </div>
          <IconButton aria-label={t("conflict.dismiss")} icon={<X />} onClick={() => setConflict(false)} />
        </div>
      )}
      <ListingForm
        key={`${listing.revision}:${listing.updatedAt ?? ""}`}
        exam={exam}
        listing={listing}
        canEdit={canEdit}
        onConflict={() => {
          setConflict(true);
          void queryClient.invalidateQueries({ queryKey: EXAMS_QUERY_KEYS.all });
        }}
        onSaved={() => setConflict(false)}
      />
    </div>
  );
}
