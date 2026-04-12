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
        /* TODO: Your solution goes here */
        HashMap<Integer,ArrayList<Integer>> noDelayPath = Traversals.bfsPaths(this.graph, this.clients);
        sol.paths = new HashMap<>(noDelayPath);
        sol.bandwidths = new ArrayList<>(bandwidths);
        sol.priorities = new HashMap<>(clients.size());

        //1. Added clients to hashmap for access

        //2. Check which clients don't satisfy the condition
        HashMap<Integer, Integer> clientsDelay = Simulator.run(this.graph, this.clients, sol);
        sortClient(noDelayPath,clientsDelay,sol);
        clientsDelay = Simulator.run(this.graph, this.clients, sol);

        //4.try alternativeBFS
        float rev1 = calRevenue(clientsDelay,noDelayPath,sol);

        boolean improvedPath = true;
        int loops = 0;
        while (improvedPath && loops < 75) {
            loops++;
            improvedPath = false;

            ArrayList<Integer> unsatisfiedClients = calUnsatisfiedClients(clientsDelay,noDelayPath);

            if (unsatisfiedClients.isEmpty()) {
                break;
            }

            HashMap<Integer, Integer> nodeMap = findRiskyNodes(unsatisfiedClients, sol);
            ArrayList<Integer> nodesSorted = sortNodesByRisk(nodeMap);

            float bestPathRevenue = rev1;
            int bestPathClientId = -1;
            ArrayList<Integer> bestNewPath = null;

            int maxBFSTries = Math.min(4, unsatisfiedClients.size());
            for (int i = 0; i < maxBFSTries; i++) {
                int clientId = unsatisfiedClients.get(i);
                ArrayList<Integer> oldPath = sol.paths.get(clientId);

                int maxAvoidNodesToTry = Math.min(1, nodesSorted.size());
                for (int j = 0; j < maxAvoidNodesToTry; j++) {
                    ArrayList<Integer> copyNodesSorted = new ArrayList<>();
                    copyNodesSorted.add(nodesSorted.get(j));

                    ArrayList<Integer> betterPath = alternativeBFS(graph, clientId, copyNodesSorted);

                    if (betterPath != null) {
                        sol.paths.put(clientId, betterPath);

                        HashMap<Integer, Integer> betterClientsDelay = Simulator.run(graph, clients, sol);
                        float rev2 = calRevenue(betterClientsDelay, noDelayPath,sol);

                        if (rev2 > bestPathRevenue) {
                            bestPathRevenue = rev2;
                            bestPathClientId = clientId;
                            bestNewPath = new ArrayList<>(betterPath);
                        }

                        sol.paths.put(clientId, oldPath);
                    }
                }
            }

            if (bestPathClientId != -1) {
                sol.paths.put(bestPathClientId, bestNewPath);
                clientsDelay = Simulator.run(this.graph, this.clients, sol);
                sortClient(noDelayPath,clientsDelay,sol);
                clientsDelay = Simulator.run(this.graph, this.clients, sol);
                rev1 = calRevenue(clientsDelay,noDelayPath,sol);
                improvedPath = true;
            }
        }

        //5. try increasing bandwidth
        boolean improvedBandwidth = true;
        int loop2 = 0;
        while (improvedBandwidth && loop2 < 75) {
            improvedBandwidth = false;
            loop2++;

            ArrayList<Integer> unsatisfiedClients = calUnsatisfiedClients(clientsDelay,noDelayPath);

            if (unsatisfiedClients.isEmpty()) {
                break;
            }

            HashMap<Integer, Integer> nodeMap = findRiskyNodes(unsatisfiedClients, sol);
            ArrayList<Integer> nodesSorted = sortNodesByRisk(nodeMap);

            float bestBandwidthRevenue = rev1;
            int bestBandwidthNodeID = -1;

            int maxBandwidthTries = Math.min(8, nodesSorted.size());
            for (int j = 0; j < maxBandwidthTries; j++) {
                Integer nodeId = nodesSorted.get(j);
                int originalBandwidth = sol.bandwidths.get(nodeId);
                sol.bandwidths.set(nodeId, originalBandwidth + 1);

                HashMap<Integer, Integer> betterDelay = Simulator.run(graph, clients, sol);
                float rev2 = calRevenue(betterDelay,noDelayPath,sol);

                if (rev2 > bestBandwidthRevenue) {
                    bestBandwidthRevenue = rev2;
                    bestBandwidthNodeID = nodeId;
                }

                sol.bandwidths.set(nodeId, originalBandwidth);
            }

            if (bestBandwidthNodeID != -1) {
                sol.bandwidths.set(bestBandwidthNodeID, sol.bandwidths.get(bestBandwidthNodeID) + 1);
                clientsDelay = Simulator.run(this.graph, this.clients, sol);
                sortClient(noDelayPath,clientsDelay,sol);
                clientsDelay = Simulator.run(this.graph, this.clients, sol);
                rev1 = calRevenue(clientsDelay,noDelayPath,sol);
                improvedBandwidth = true;
            }
        }

        return sol;
    }

    private ArrayList<Integer> calUnsatisfiedClients(HashMap<Integer, Integer> clientsDelay, HashMap<Integer, ArrayList<Integer>> noDelayPath) {
       ArrayList<Integer> unsatisfiedClients = new ArrayList<>();
       for (Client client : clients) {
           if (clientsDelay.get(client.id) > client.alpha *(noDelayPath.get(client.id).size()-1)) {
               unsatisfiedClients.add(client.id);
           }
       }
       return unsatisfiedClients;
    }

    private HashMap<Integer,Integer> findRiskyNodes(ArrayList<Integer> unsatisfiedClients,SolutionObject sol) {
        HashMap<Integer, Integer> nodeCount = new HashMap<>();
        HashMap<Integer,Integer> nodeMap = new HashMap<>();
        for (int clientId : unsatisfiedClients) {
            ArrayList<Integer> pathOfClient = sol.paths.get(clientId);
            if (pathOfClient != null) {
                for (Integer nodeId : pathOfClient) {
                    if (nodeCount.get(nodeId) == null) {
                        nodeCount.put(nodeId, 1);
                    }
                    else nodeCount.put(nodeId, nodeCount.get(nodeId) + 1);
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

    private float calRevenue(HashMap<Integer,Integer> clientsDelay, HashMap<Integer,ArrayList<Integer>> noDelayPath, SolutionObject sol) {
        boolean satisfiedClients = true;
        int total = 0;
        for (Client client: this.clients){
            if (clientsDelay.get(client.id) > client.alpha * (noDelayPath.get(client.id).size()-1)) {
                satisfiedClients = false;
            }
            total +=client.payment;
        }
        int increasedBandwidth = 0;
        for (int i = 0; i < sol.bandwidths.size(); i++) {
            increasedBandwidth += sol.bandwidths.get(i)- bandwidths.get(i);
        }
        float bandwidthCost = - (info.costBandwidth * increasedBandwidth);

        if (satisfiedClients) {
            return total + bandwidthCost;
        }
        return bandwidthCost;
    }

    private ArrayList<Integer> alternativeBFS (Graph graph, int clientId, ArrayList<java.lang.Integer> avoidedNodes){
        Queue<Integer> q = new LinkedList<>();
        HashMap<Integer, Integer> parent = new HashMap<>();

        boolean[] visited = new boolean[graph.size()];
        boolean[] avoidNodes = new boolean[graph.size()];

        for (int node: avoidedNodes) {
            avoidNodes[node] = true;
        }

        q.add(graph.contentProvider);
        parent.put(graph.contentProvider, -1);
        visited[graph.contentProvider] = true;

        while (!q.isEmpty()){
            int current = q.poll();

            if (current == clientId){
                ArrayList<java.lang.Integer> currentPath = new ArrayList<>();
                int x = clientId;
                while (x != -1){
                    currentPath.add(0, x);
                    x = parent.get(x);
                }
                return currentPath;
            }

            ArrayList<java.lang.Integer> neighborsOfNode = graph.get(current);
            if (neighborsOfNode != null){
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

    private ArrayList<Integer> sortNodesByRisk (HashMap<Integer,Integer> nodeMap){
        ArrayList<Integer> nodesSorted = new ArrayList<>();
        for (Integer nodeId : nodeMap.keySet()){
            nodesSorted.add(nodeId);
        }
        nodesSorted.sort((x,y) -> Integer.compare(nodeMap.get(y),nodeMap.get(x)));
        return nodesSorted;
    }

    private void sortClient (HashMap<Integer,ArrayList<Integer>> noDelayPath, HashMap<Integer, Integer> clientsDelay, SolutionObject sol) {
        ArrayList<Pair<Integer,Double>> clientsScore = new ArrayList<>();
        for (Client client : this.clients){
            double score = (double)clientsDelay.get(client.id) /(client.alpha * (noDelayPath.get(client.id).size()-1));
            clientsScore.add(new Pair<>(client.id, score));
        }
        clientsScore.sort((a, b) -> Double.compare(b.getSecond(),a.getSecond()));

        sol.priorities.clear();
        for (int i = 0; i<clientsScore.size(); i++){
            sol.priorities.put(clientsScore.get(i).getFirst(),clientsScore.size() - i);
        }
    }
}
