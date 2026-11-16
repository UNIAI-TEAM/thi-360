package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Lõi mạng luồng (Dinic trên danh sách cạnh kề) và union-find của bộ giải (spec 5.4).
 *
 * <p>Giai đoạn 2 dựng trên lõi này mạng hai lớp: nguồn → chỉ tiêu phía nhóm → câu (sức chứa 1,
 * gộp các bản ghi chung khóa bằng {@link Dsu}) → chỉ tiêu phía môn → đích. Cạnh dạng {đích, chỉ số
 * cạnh ngược, sức chứa còn lại}; id cạnh thuận luôn chẵn. Deadline được kiểm định kỳ trong BFS/DFS.
 */
final class MaxFlow {

    private MaxFlow() {
    }

    /** Union-find gộp các entry ứng viên chung khóa (questionId, childId) về một nút. */
    static final class Dsu {
        private final int[] parent;

        Dsu(int n) {
            parent = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
            }
        }

        int find(int x) {
            int r = x;
            while (parent[r] != r) {
                r = parent[r];
            }
            while (parent[x] != r) {
                int next = parent[x];
                parent[x] = r;
                x = next;
            }
            return r;
        }

        void union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra != rb) {
                parent[rb] = ra;
            }
        }
    }

    /** Dinic: cạnh dạng {đích, chỉ số cạnh ngược trong danh sách kề của đích, sức chứa còn lại}. */
    static final class Dinic {
        final int nodeCount;
        private final List<int[]> edges = new ArrayList<>();
        private final List<List<Integer>> adj;
        private final int[] level;
        private final int[] it;

        Dinic(int n) {
            this.nodeCount = n;
            adj = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                adj.add(new ArrayList<>());
            }
            level = new int[n];
            it = new int[n];
        }

        /** @return id của cạnh thuận (luôn chẵn; cạnh ngược là id + 1). */
        int addEdge(int u, int v, int cap) {
            int id = edges.size();
            edges.add(new int[]{v, adj.get(v).size(), cap});
            edges.add(new int[]{u, adj.get(u).size(), 0});
            adj.get(u).add(id);
            adj.get(v).add(id + 1);
            return id;
        }

        /** Luồng đã đẩy qua cạnh thuận = sức chứa của cạnh ngược. */
        int flow(int forwardEdgeId) {
            return edges.get(forwardEdgeId + 1)[2];
        }

        long maxFlow(int s, int t, Runnable deadlineCheck) {
            long flow = 0;
            while (bfs(s, t, deadlineCheck)) {
                deadlineCheck.run();
                Arrays.fill(it, 0);
                long pushed;
                while ((pushed = dfs(s, t, Long.MAX_VALUE, deadlineCheck)) > 0) {
                    flow += pushed;
                }
            }
            return flow;
        }

        private boolean bfs(int s, int t, Runnable deadlineCheck) {
            Arrays.fill(level, -1);
            Deque<Integer> q = new ArrayDeque<>();
            level[s] = 0;
            q.add(s);
            int scans = 0;
            while (!q.isEmpty()) {
                if ((scans++ & 0x3FF) == 0) {
                    deadlineCheck.run();
                }
                int u = q.poll();
                for (int e : adj.get(u)) {
                    int[] ed = edges.get(e);
                    if (ed[2] > 0 && level[ed[0]] < 0) {
                        level[ed[0]] = level[u] + 1;
                        q.add(ed[0]);
                    }
                }
            }
            return level[t] >= 0;
        }

        private long dfs(int u, int t, long f, Runnable deadlineCheck) {
            if (u == t) {
                return f;
            }
            deadlineCheck.run();
            List<Integer> ad = adj.get(u);
            while (it[u] < ad.size()) {
                int e = ad.get(it[u]);
                int[] ed = edges.get(e);
                if (ed[2] > 0 && level[ed[0]] == level[u] + 1) {
                    long pushed = dfs(ed[0], t, Math.min(f, ed[2]), deadlineCheck);
                    if (pushed > 0) {
                        ed[2] -= (int) pushed;
                        edges.get(e ^ 1)[2] += (int) pushed;
                        return pushed;
                    }
                }
                it[u]++;
            }
            return 0;
        }
    }
}
