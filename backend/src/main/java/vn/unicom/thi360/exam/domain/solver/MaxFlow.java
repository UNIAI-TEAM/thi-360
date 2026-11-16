package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mạng luồng chính xác cho trường hợp đơn giản của spec 5.4: mọi ứng viên usable có đúng một đơn vị đếm
 * và mọi khối chỉ có chỉ tiêu một chiều (ô COMBINED hoặc một chiều SEPARATE).
 *
 * <p>Mạng: nguồn → chỉ tiêu (sức chứa = quota) → ứng viên (sức chứa 1) → đích. Nút ứng viên là toàn
 * đường, không theo khối: các bản ghi ứng viên có chung khóa (questionId, childId) được gộp thành một
 * nút nên mỗi câu / câu con chỉ được dùng một lần trên cả đường. Luồng đầy tổng chỉ tiêu nghĩa là VALID.
 *
 * <p>Dinic trên danh sách cạnh kề; đồ thị ba tầng nên số pha nhỏ. Một cạnh chỉ tiêu → ứng viên có luồng
 * = 1 nghĩa là ứng viên đó được chọn (đề chứng minh).
 */
final class MaxFlow {

    private final Runnable deadlineCheck;

    MaxFlow(Runnable deadlineCheck) {
        this.deadlineCheck = deadlineCheck;
    }

    /**
     * Giải toàn bộ các khối cùng lúc.
     *
     * @param blocks các khối đã chuẩn bị theo thứ tự đường
     * @return mảng song song {@code blocks}: chỉ số ứng viên được chọn mỗi khối; null khi không đủ luồng
     */
    List<List<Integer>> solve(List<BlockContext> blocks) {
        int reqCount = 0;
        int entryCount = 0;
        for (BlockContext b : blocks) {
            reqCount += b.reqs().size();
            entryCount += b.cands().size();
        }
        // nút: 0 = nguồn; 1..reqCount = chỉ tiêu; tiếp = ứng viên (đánh theo chỉ số entry gốc); cuối = đích
        Dinic net = new Dinic(2 + reqCount + entryCount);
        int source = 0;
        int sink = net.nodeCount - 1;

        int[][] reqNodes = new int[blocks.size()][];
        int node = 1;
        long totalQuota = 0;
        for (int b = 0; b < blocks.size(); b++) {
            BlockContext ctx = blocks.get(b);
            int[] rn = new int[ctx.reqs().size()];
            for (int r = 0; r < rn.length; r++) {
                rn[r] = node++;
                int q = ctx.reqs().get(r).quota();
                totalQuota += q;
                net.addEdge(source, rn[r], q);
            }
            reqNodes[b] = rn;
        }

        deadlineCheck.run();
        int candNodeBase = node;

        // gộp các bản ghi ứng viên usable chung khóa (questionId, childId) về một nút bằng union-find;
        // ứng viên không usable không gộp: cầu nối qua khóa của nó sẽ cấm nhầm hai ứng viên hợp lệ
        Dsu dsu = new Dsu(entryCount);
        Map<String, Integer> entryOfKey = new HashMap<>(entryCount * 2);
        int ei = 0;
        for (BlockContext ctx : blocks) {
            for (int i = 0; i < ctx.cands().size(); i++) {
                if ((ei & 0x3FF) == 0) {
                    deadlineCheck.run();
                }
                if (ctx.usable()[i]) {
                    for (String k : ctx.keys()[i]) {
                        Integer known = entryOfKey.get(k);
                        if (known == null) {
                            entryOfKey.put(k, ei);
                        } else {
                            dsu.union(ei, known);
                        }
                    }
                }
                ei++;
            }
        }

        int[][] flowEdge = new int[entryCount][]; // id cạnh chỉ tiêu→ứng viên của từng entry
        boolean[] sinkEdgeDone = new boolean[entryCount];
        ei = 0;
        for (int b = 0; b < blocks.size(); b++) {
            BlockContext ctx = blocks.get(b);
            int[] rn = reqNodes[b];
            for (int i = 0; i < ctx.cands().size(); i++) {
                if ((ei & 0x3FF) == 0) {
                    deadlineCheck.run();
                }
                int root = dsu.find(ei);
                int candNode = candNodeBase + root;
                if (!sinkEdgeDone[root]) {
                    sinkEdgeDone[root] = true;
                    net.addEdge(candNode, sink, 1);
                }
                if (ctx.usable()[i]) {
                    int[] vec = ctx.contrib()[i];
                    int[] edges = new int[rn.length];
                    Arrays.fill(edges, -1);
                    for (int r = 0; r < rn.length; r++) {
                        if (vec[r] > 0) {
                            edges[r] = net.addEdge(rn[r], candNode, 1);
                        }
                    }
                    flowEdge[ei] = edges;
                }
                ei++;
            }
        }

        long flow = net.maxFlow(source, sink, deadlineCheck);
        if (flow < totalQuota) {
            return null;
        }

        List<List<Integer>> out = new ArrayList<>(blocks.size());
        ei = 0;
        for (int b = 0; b < blocks.size(); b++) {
            BlockContext ctx = blocks.get(b);
            List<Integer> picked = new ArrayList<>();
            for (int i = 0; i < ctx.cands().size(); i++) {
                int[] edges = flowEdge[ei];
                if (edges != null) {
                    for (int e : edges) {
                        if (e >= 0 && net.flow(e) > 0) {
                            picked.add(i);
                            break;
                        }
                    }
                }
                ei++;
            }
            out.add(picked);
        }
        return out;
    }

    /** Union-find trên chỉ số entry để gộp các ứng viên chung khóa. */
    private static final class Dsu {
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
    private static final class Dinic {
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
