"use client";

import { useQuery } from "@tanstack/react-query";
import { Button, Card, EmptyState, SectionTitle, Skeleton } from "@thi360/ui";
import {
  Award,
  BookOpen,
  ClipboardList,
  KeyRound,
  LifeBuoy,
  TriangleAlert,
} from "lucide-react";
import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { usePermissions } from "@/features/session/session-provider";
import { type StudentLearning, type UsersApiDeps, USERS_QUERY_KEYS } from "../api";

type LearningBlockKey =
  | "entitlements"
  | "attempts"
  | "assessments"
  | "practice"
  | "supportRequests";

interface LearningBlockConfig {
  key: LearningBlockKey;
  icon: ReactNode;
  fullWidth?: boolean;
}

const LEARNING_BLOCKS: readonly LearningBlockConfig[] = [
  { key: "entitlements", icon: <KeyRound /> },
  { key: "attempts", icon: <ClipboardList /> },
  { key: "assessments", icon: <Award /> },
  { key: "practice", icon: <BookOpen /> },
  { key: "supportRequests", icon: <LifeBuoy />, fullWidth: true },
];

export interface LearningProfileTabProps {
  userId: string;
  getUserLearning: UsersApiDeps["getUserLearning"];
}

export function LearningProfileTab({ userId, getUserLearning }: LearningProfileTabProps) {
  const t = useTranslations("users");
  const common = useTranslations("common");
  const { can } = usePermissions();
  const canViewLearning = can("USER.LEARNING_VIEW");

  const learningQuery = useQuery({
    queryKey: USERS_QUERY_KEYS.learning(userId),
    queryFn: () => getUserLearning(userId),
    enabled: canViewLearning,
  });

  if (!canViewLearning) {
    return null;
  }

  if (learningQuery.isLoading) {
    return (
      <div
        role="status"
        aria-label={common("loading")}
        className="grid grid-cols-1 gap-4 lg:grid-cols-2"
      >
        {LEARNING_BLOCKS.map((block) => (
          <Card
            key={block.key}
            className={block.fullWidth ? "flex flex-col gap-4 lg:col-span-2" : "flex flex-col gap-4"}
          >
            <Skeleton className="h-6 w-48" />
            <Skeleton className="h-32 w-full" />
          </Card>
        ))}
      </div>
    );
  }

  if (learningQuery.isError) {
    return (
      <Card>
        <EmptyState
          icon={<TriangleAlert />}
          title={t("learning.errorTitle")}
          description={t("learning.errorDescription")}
          action={
            <Button variant="secondary" onClick={() => void learningQuery.refetch()}>
              {common("retry")}
            </Button>
          }
        />
      </Card>
    );
  }

  const data: StudentLearning | undefined = learningQuery.data;
  if (!data) {
    return null;
  }

  return (
    <div
      data-slot="learning-profile-tab"
      data-data-available={String(data.dataAvailable)}
      className="grid grid-cols-1 gap-4 lg:grid-cols-2"
    >
      {LEARNING_BLOCKS.map((block) => {
        const items = data[block.key];
        const showEmpty = !data.dataAvailable || items.length === 0;
        return (
          <Card
            key={block.key}
            data-slot="learning-profile-block"
            data-block={block.key}
            className={block.fullWidth ? "flex flex-col gap-2 lg:col-span-2" : "flex flex-col gap-2"}
          >
            <SectionTitle title={t(`learning.blocks.${block.key}.title`)} />
            {showEmpty && (
              <EmptyState
                icon={block.icon}
                title={t("learning.emptyTitle")}
                description={t(`learning.blocks.${block.key}.description`)}
                className="py-8"
              />
            )}
          </Card>
        );
      })}
    </div>
  );
}
