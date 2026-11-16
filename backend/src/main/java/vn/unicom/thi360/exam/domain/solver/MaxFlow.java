package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mạng luồng chính xác hai lớp của spec 5.4 (bước 1) cho một bài toán con theo môn.
 *
 * <p>Áp dụng khi bài toán con chỉ có câu độc lập (mọi ứng viên usable một đơn vị), môn nằm ở một
 * mạch duy nhất, và mỗi phía xếp được thành tối đa một phân hoạch: phía nhóm là chỉ tiêu TỔNG của
 * nhóm (không có ma trận/chỉ tiêu riêng) hoặc đúng một phân hoạch ma trận của nhóm; phía môn là tối
 * đa một phân hoạch (ô COMBINED, hoặc một chiều SEPARATE, hoặc dạng trả lời).
 *
 * <p>Mạng: nguồn → chỉ tiêu phía nhóm (sức chứa = quota) → ứng viên (sức chứa 1) → chỉ tiêu phía
 * môn (sức chứa = quota) → đích. Một đơn vị khớp đúng một mục chỉ tiêu trong phân hoạch đã khai
 * (Prep bảo đảm), nên mỗi ứng viên usable có đúng một cạnh vào và một cạnh ra. Nút ứng viên gộp
 * các bản ghi usable chung khóa (questionId, childId) bằng union-find: mỗi câu / câu con chỉ dùng
 * một lần trên cả bài toán con (R1-1: chỉ gộp ứng viên usable). Luồng đầy tổng chỉ tiêu phía nhóm
 * nghĩa là VALID.
 */
final class MaxFlow {

    private final Runnable deadlineCheck;
    private boolean inapplicable;

    MaxFlow(Runnable deadlineCheck) {
        this.deadlineCheck = deadlineCheck;
    }

    /** True khi solve() vừa bỏ cuộc vì mạng không mô hình hóa được (khác với luồng không đầy). */
    boolean inapplicable() {
        return inapplicable;
    }

    /**
     * Bài toán con có giải được bằng mạng luồng hai lớp không (spec 5.4 bước 1): mọi nhóm là
     * SINGLE, không có chỉ tiêu ngoài phân hoạch (không năng lực, nhóm MAPID, lọc câu con), mỗi
     * phía tối đa một phân hoạch, và mọi ứng viên usable là câu độc lập một đơn vị.
     */
    boolean applies(Prep.SubproblemContext ctx) {
        int subjectDims = 0;
        for (int[] dim : ctx.dims()) {
            if (dim[1] <= ctx.subjectReqs()) {
                subjectDims++;
            }
        }
        if (subjectDims > 1) {
            return false;
        }
        List<Prep.Requirement> reqs = ctx.reqs();
        for (int g = 0; g < ctx.groups().size(); g++) {
            Prep.ResolvedGroup rg = ctx.groups().get(g);
            if (rg.group().kind() != GroupQuota.GroupKind.SINGLE) {
                return false;
            }
            // chỉ tiêu của nhóm g ngoài TỔNG: chỉ được là một phân hoạch (ô hoặc một chiều)
            int groupDims = 0;
            for (int[] dim : ctx.dims()) {
                if (dim[0] >= ctx.reqFrom()[g] && dim[1] <= ctx.reqTo()[g]) {
                    groupDims++;
                }
            }
            if (groupDims > 1) {
                return false;
            }
            for (int r = ctx.reqFrom()[g] + 1; r < ctx.reqTo()[g]; r++) {
                Prep.Requirement.Kind kind = reqs.get(r).kind();
                if (kind == Prep.Requirement.Kind.COMPETENCY
                        || kind == Prep.Requirement.Kind.MAPID_GROUP) {
                    return false;
                }
            }
            for (int i = 0; i < ctx.cands().get(g).size(); i++) {
                if (ctx.usable()[g][i] && ctx.units()[g][i] != 1) {
                    return false;
                }
            }
            deadlineCheck();
        }
        return true;
    }

    private void deadlineCheck() {
        deadlineCheck.run();
    }

    /**
     * Giải bài toán con bằng mạng luồng hai lớp.
     *
     * @return chỉ số ứng viên được chọn của từng nhóm (song song {@code ctx.groups()}); null khi
     *         luồng không đầy
     */
    List<List<Integer>> solve(Prep.SubproblemContext ctx) {
        int nG = ctx.groups().size();
        // mục chỉ tiêu của mỗi phía mà luồng đi qua: phía nhóm = phân hoạch của nhóm (hoặc TỔNG);
        // phía môn = phân hoạch duy nhất của môn (hoặc không có → câu nối thẳng đích)
        int[] groupReqOf = new int[nG]; // một nút chỉ tiêu cho mỗi nhóm khi nhóm không có phân hoạch
        List<int[]> groupReqNodes = new ArrayList<>(nG);
        int[] subjectReqNodes;

        int reqCount = 0;
        for (int g = 0; g < nG; g++) {
            int[] dim = singleDimOf(ctx, g);
            int from = dim != null ? dim[0] : ctx.reqFrom()[g];
            int to = dim != null ? dim[1] : ctx.reqFrom()[g] + 1;
            groupReqNodes.add(new int[]{from, to});
            reqCount += to - from;
        }
        int[] subjectDim = null;
        for (int[] dim : ctx.dims()) {
            if (dim[1] <= ctx.subjectReqs()) {
                subjectDim = dim;
                break;
            }
        }
        int subjectReqCount = subjectDim != null ? subjectDim[1] - subjectDim[0] : 0;

        int entryCount = 0;
        for (int g = 0; g < nG; g++) {
            entryCount += ctx.cands().get(g).size();
        }
        // nút: 0 = nguồn; 1..reqCount = chỉ tiêu phía nhóm; tiếp = chỉ tiêu phía môn;
        // tiếp = ứng viên (theo chỉ số entry gốc); cuối = đích
        Dinic net = new Dinic(2 + reqCount + subjectReqCount + entryCount);
        int source = 0;
        int sink = net.nodeCount - 1;

        int node = 1;
        int[][] reqNodes = new int[nG][];
        long totalQuota = 0;
        for (int g = 0; g < nG; g++) {
            int[] range = groupReqNodes.get(g);
            int[] rn = new int[range[1] - range[0]];
            for (int k = 0; k < rn.length; k++) {
                rn[k] = node++;
                int q = ctx.reqs().get(range[0] + k).quota();
                totalQuota += q;
                net.addEdge(source, rn[k], q);
            }
            reqNodes[g] = rn;
        }
        subjectReqNodes = new int[subjectReqCount];
        for (int k = 0; k < subjectReqCount; k++) {
            subjectReqNodes[k] = node++;
            int q = ctx.reqs().get(subjectDim[0] + k).quota();
            net.addEdge(subjectReqNodes[k], sink, q);
        }

        deadlineCheck.run();
        int candNodeBase = node;

        // gộp các bản ghi ứng viên usable chung khóa về một nút bằng union-find (R1-1: chỉ entry
        // usable mới gộp; cầu nối qua khóa của entry không usable sẽ cấm nhầm ứng viên hợp lệ)
        Dsu dsu = new Dsu(entryCount);
        Map<String, Integer> entryOfKey = new HashMap<>(entryCount * 2);
        int ei = 0;
        for (int g = 0; g < nG; g++) {
            for (int i = 0; i < ctx.cands().get(g).size(); i++) {
                if ((ei & 0x3FF) == 0) {
                    deadlineCheck.run();
                }
                if (ctx.usable()[g][i]) {
                    for (String k : ctx.keys()[g][i]) {
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

        // cạnh chỉ tiêu phía nhóm → ứng viên → chỉ tiêu phía môn (mỗi usable đúng một cạnh mỗi phía).
        // Một nút gộp chỉ có một cạnh ra: các entry chung khóa phải cùng mục tiêu phía môn — entry
        // chung khóa mà khác mục chỉ tiêu phía môn không mô hình được bằng mạng này (đơn vị chọn sẽ
        // đóng góp sai phía môn), bộ giải chuyển sang quay lui (inapplicable).
        int[][] flowEdge = new int[entryCount][];
        int[] outTarget = new int[entryCount];
        boolean[] outDone = new boolean[entryCount];
        ei = 0;
        for (int g = 0; g < nG; g++) {
            int[] rn = reqNodes[g];
            int[] range = groupReqNodes.get(g);
            for (int i = 0; i < ctx.cands().get(g).size(); i++) {
                if ((ei & 0x3FF) == 0) {
                    deadlineCheck.run();
                }
                if (ctx.usable()[g][i]) {
                    int root = dsu.find(ei);
                    int candNode = candNodeBase + root;
                    int[] vec = ctx.contrib()[g][i];
                    // cạnh vào: đúng một chỉ tiêu phía nhóm khớp (phân hoạch hoặc TỔNG)
                    int inEdge = -1;
                    for (int k = 0; k < rn.length; k++) {
                        if (vec[range[0] + k] > 0) {
                            inEdge = net.addEdge(rn[k], candNode, 1);
                            break;
                        }
                    }
                    // cạnh ra: đúng một chỉ tiêu phía môn khớp, hoặc thẳng đích khi không có chỉ tiêu môn
                    int target = -1; // -1 = thẳng đích
                    if (subjectDim != null) {
                        for (int k = 0; k < subjectReqCount; k++) {
                            if (vec[subjectDim[0] + k] > 0) {
                                target = k;
                                break;
                            }
                        }
                    }
                    if (outDone[root] && outTarget[root] != target) {
                        inapplicable = true;
                        return null;
                    }
                    if (!outDone[root]) {
                        outDone[root] = true;
                        outTarget[root] = target;
                        if (target < 0) {
                            net.addEdge(candNode, sink, 1);
                        } else {
                            net.addEdge(candNode, subjectReqNodes[target], 1);
                        }
                    }
                    flowEdge[ei] = new int[]{inEdge};
                }
                ei++;
            }
        }

        long flow = net.maxFlow(source, sink, deadlineCheck);
        if (flow < totalQuota) {
            return null;
        }

        List<List<Integer>> out = new ArrayList<>(nG);
        ei = 0;
        for (int g = 0; g < nG; g++) {
            List<Integer> picked = new ArrayList<>();
            for (int i = 0; i < ctx.cands().get(g).size(); i++) {
                int[] edges = flowEdge[ei];
                if (edges != null && net.flow(edges[0]) > 0) {
                    picked.add(i);
                }
                ei++;
            }
            out.add(picked);
        }
        return out;
    }

    /** Phân hoạch duy nhất của nhóm g nếu có (nằm trong khoảng chỉ tiêu của nó), null nếu không. */
    private static int[] singleDimOf(Prep.SubproblemContext ctx, int g) {
        int[] found = null;
        for (int[] dim : ctx.dims()) {
            if (dim[0] >= ctx.reqFrom()[g] && dim[1] <= ctx.reqTo()[g]) {
                if (found != null) {
                    return null; // không gọi khi applies() đã lọc; phòng thủ
                }
                found = dim;
            }
        }
        return found;
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
