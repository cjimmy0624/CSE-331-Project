package ub.cse.algo;

import ub.cse.algo.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class Solution {

    private Info info;
    private Graph graph;
    private ArrayList<Client> clients;
    private ArrayList<Integer> bandwidths;

    /**
     * Basic Constructor
     *
     * @param info: data parsed from input file
     */
    public Solution(Info info) {
        this.info = info;
        this.graph = info.graph;
        this.clients = info.clients;
        this.bandwidths = info.bandwidths;
    }

    /**
     * Method that returns the calculated
     * SolutionObject as found by your algorithm
     *
     * @return SolutionObject containing the paths, priorities and bandwidths
     */
    public SolutionObject outputPaths() {
        SolutionObject sol = new SolutionObject();
        HashMap<Integer, ArrayList<Integer>> noDelayPath = Traversals.bfsPaths(this.graph, this.clients);
        sol.paths = new HashMap<>(noDelayPath);
        sol.bandwidths = new ArrayList<>(bandwidths);

        sol.priorities = new HashMap<>();
        for (int i = 0; i < clients.size(); i++) {
            sol.priorities.put(clients.get(i).id, i);
        }

        HashMap<Integer, Integer> delayPath = Simulator.run(this.graph, this.clients, sol);
        ArrayList<Integer> unsatisfiedClients = calUnsatisfiedClients(delayPath, noDelayPath);

        sol.priorities = setPriorities(sol, unsatisfiedClients);

        delayPath = Simulator.run(this.graph, this.clients, sol);
        unsatisfiedClients = calUnsatisfiedClients(delayPath, noDelayPath);

        HashMap<Integer, Integer> congestedNode = findRiskyNodes(unsatisfiedClients, sol);

        for (Integer nodeId : congestedNode.keySet()) {
            int node = congestedNode.get(nodeId);
            int oldBandwidth = sol.bandwidths.get(nodeId);
            sol.bandwidths.set(nodeId, oldBandwidth + node);
        }
        delayPath = Simulator.run(this.graph, this.clients, sol);
        unsatisfiedClients = calUnsatisfiedClients(delayPath, noDelayPath);

        congestedNode = findRiskyNodes(unsatisfiedClients, sol);
        ArrayList<Integer> avoidedNodes = sortNodesByRisk(congestedNode);

        ArrayList<Integer> topRiskyNodes = new ArrayList<>();
        int numTries = 3;

        if (avoidedNodes.size() < 3) {
            numTries = avoidedNodes.size();
        }

        for (int i = 0; i < numTries; i++) {
            topRiskyNodes.add(avoidedNodes.get(i));
        }

        avoidedNodes = topRiskyNodes;

        for (int clientId : unsatisfiedClients) {
            ArrayList<Integer> newPath = alternativeBFS(this.graph, clientId, avoidedNodes);

            if (newPath != null) {
                ArrayList<Integer> oldPath = sol.paths.get(clientId);

                if (oldPath == null || newPath.size() <= oldPath.size() + 2) {
                    sol.paths.put(clientId, newPath);
                }
            }
        }

        delayPath = Simulator.run(this.graph, this.clients, sol);
        unsatisfiedClients = calUnsatisfiedClients(delayPath, noDelayPath);

        sol.priorities = setPriorities(sol,unsatisfiedClients);
        return sol;
    }

    private HashMap<Integer, Integer> setPriorities(SolutionObject sol, ArrayList<Integer> unsatisfiedClients) {
        HashMap<Integer, Integer> priorities = new HashMap<>();
        ArrayList<Pair<Integer, Double>> scoreList = new ArrayList<>();

        HashMap<Integer, Integer> riskyNodes = findRiskyNodes(unsatisfiedClients, sol);

        for (Client client : clients) {
            ArrayList<Integer> path = sol.paths.get(client.id);

            if (path == null || path.isEmpty()) {
                continue;
            }
            int riskyScore = 0;
            for (int node : path) {
                if (riskyNodes.get(node) != null) {
                    riskyScore += riskyNodes.get(node);
                }
            }
            double score = (client.payment / client.alpha) + 10.0 * (path.size() - 1) + 20.0 * riskyScore;

            scoreList.add(new Pair<>(client.id, score));
        }

        scoreList.sort((x, y) -> Double.compare(y.getSecond(), x.getSecond()));

        for (int i = 0; i < scoreList.size(); i++) {
            priorities.put(scoreList.get(i).getFirst(), scoreList.size() - 1 - i);
        }

        return priorities;
    }

    private ArrayList<Integer> calUnsatisfiedClients(HashMap<Integer, Integer> clientsDelay, HashMap<Integer, ArrayList<Integer>> noDelayPath) {
        ArrayList<Integer> unsatisfiedClients = new ArrayList<>();
        for (Client client : clients) {
            int delay = clientsDelay.get(client.id);
            int shortestPathLength = noDelayPath.get(client.id).size() - 1;

            if (delay > client.alpha * shortestPathLength) {
                unsatisfiedClients.add(client.id);
            }
        }
        return unsatisfiedClients;
    }

    private HashMap<Integer, Integer> findRiskyNodes(ArrayList<Integer> unsatisfiedClients, SolutionObject sol) {
        HashMap<Integer, Integer> nodeCount = new HashMap<>();
        HashMap<Integer, Integer> nodeMap = new HashMap<>();

        for (int clientId : unsatisfiedClients) {
            ArrayList<Integer> pathOfClient = sol.paths.get(clientId);

            if (pathOfClient != null) {
                for (Integer nodeId : pathOfClient) {
                    if (nodeId == graph.contentProvider) {
                        continue;
                    }

                    if (nodeCount.get(nodeId) == null){
                        nodeCount.put(nodeId, 1);
                    }else
                        nodeCount.put(nodeId, nodeCount.get(nodeId) + 1);
                }
            }
        }

        for (Integer nodeId : nodeCount.keySet()) {
            int clientsVisited = nodeCount.get(nodeId);
            int nodeBandwidth = sol.bandwidths.get(nodeId);

            if (clientsVisited > nodeBandwidth) {
                nodeMap.put(nodeId, clientsVisited - nodeBandwidth);
            }
        }

        return nodeMap;
    }

    private ArrayList<Integer> alternativeBFS(Graph graph, int clientId, ArrayList<Integer> avoidedNodes) {
        Queue<Integer> q = new LinkedList<>();
        HashMap<Integer, Integer> parent = new HashMap<>();

        boolean[] visited = new boolean[graph.size()];
        boolean[] avoidNodes = new boolean[graph.size()];

        for (int node : avoidedNodes) {
            avoidNodes[node] = true;
        }

        q.add(graph.contentProvider);
        parent.put(graph.contentProvider, -1);
        visited[graph.contentProvider] = true;

        while (!q.isEmpty()) {
            int current = q.poll();

            if (current == clientId) {
                ArrayList<Integer> currentPath = new ArrayList<>();
                int x = clientId;

                while (x != -1) {
                    currentPath.add(0, x);
                    x = parent.get(x);
                }

                return currentPath;
            }

            ArrayList<Integer> neighborsOfNode = graph.get(current);

            if (neighborsOfNode != null) {
                for (int neighbor : neighborsOfNode) {
                    if (!avoidNodes[neighbor] && !visited[neighbor]) {
                        visited[neighbor] = true;
                        q.add(neighbor);
                        parent.put(neighbor, current);
                    }
                }
            }
        }

        return null;
    }

    private ArrayList<Integer> sortNodesByRisk(HashMap<Integer, Integer> nodeMap) {
        ArrayList<Integer> nodesSorted = new ArrayList<>();

        for (Integer nodeId : nodeMap.keySet()) {
            nodesSorted.add(nodeId);
        }

        nodesSorted.sort((x, y) -> Integer.compare(nodeMap.get(y), nodeMap.get(x)));

        return nodesSorted;
    }
}