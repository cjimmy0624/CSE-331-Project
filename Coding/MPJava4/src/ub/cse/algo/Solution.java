package ub.cse.algo;


import ub.cse.algo.util.Pair;

import java.util.*;

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
        sol.paths = Traversals.bfsPaths(this.graph, this.clients);
        sol.bandwidths = new ArrayList<>(bandwidths);

        HashMap<Integer, Client> clientsMap = new HashMap<>();
        for (Client client : clients) {
            clientsMap.put(client.id, client);
        }

        HashMap<Integer, Integer> clientsDelay = Simulator.run(this.graph, this.clients, sol);
        float rev1 = calRevenue(clientsDelay, false, sol, clientsMap);

        boolean improvedPath = true;
        int loops = 0;
        while (improvedPath && loops < 75) {
            loops++;
            improvedPath = false;

            ArrayList<Pair<Integer, Double>> clientsSorted = sortByBetaAndAlpha(clients, clientsDelay, sol.paths);
            HashMap<Integer, Integer> nodeMap = findRiskyNodes(clientsSorted, sol.paths, sol.bandwidths);
            ArrayList<Integer> nodesSorted = sortNodesByRisk(nodeMap);

            float bestPathRevenue = rev1;
            int bestPathClientId = -1;
            ArrayList<Integer> bestNewPath = null;

            int maxBFSTries = Math.min(4, clientsSorted.size());
            for (int i = 0; i < maxBFSTries; i++) {
                int clientId = clientsSorted.get(i).getFirst();
                ArrayList<Integer> oldPath = sol.paths.get(clientId);

                int maxAvoidNodesToTry = Math.min(1, nodesSorted.size());
                for (int j = 0; j < maxAvoidNodesToTry; j++) {
                    ArrayList<Integer> copyNodesSorted = new ArrayList<>();
                    copyNodesSorted.add(nodesSorted.get(j));

                    ArrayList<Integer> betterPath = alternativeBFS(graph, clientId, copyNodesSorted);

                    if (betterPath != null) {
                        sol.paths.put(clientId, betterPath);

                        HashMap<Integer, Integer> betterClientsDelay = Simulator.run(graph, clients, sol);
                        float rev2 = calRevenue(betterClientsDelay, false, sol, clientsMap);

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
                rev1 = bestPathRevenue;
                clientsDelay = Simulator.run(this.graph, this.clients, sol);
                improvedPath = true;
            }
        }

        boolean improvedBandwidth = true;
        int loop2 = 0;
        while (improvedBandwidth && loop2 < 75) {
            improvedBandwidth = false;
            loop2++;

            ArrayList<Pair<Integer, Double>> clientsSorted = sortByBetaAndAlpha(clients, clientsDelay, sol.paths);
            HashMap<Integer, Integer> nodeMap = findRiskyNodes(clientsSorted, sol.paths, sol.bandwidths);
            ArrayList<Integer> nodesSorted = sortNodesByRisk(nodeMap);

            float bestBandwidthRevenue = rev1;
            int bestBandwidthNodeID = -1;

            int maxBandwidthTries = Math.min(8, nodesSorted.size());
            for (int j = 0; j < maxBandwidthTries; j++) {
                Integer nodeId = nodesSorted.get(j);
                int originalBandwidth = sol.bandwidths.get(nodeId);

                sol.bandwidths.set(nodeId, originalBandwidth + 1);

                HashMap<Integer, Integer> betterDelay = Simulator.run(graph, clients, sol);
                float rev2 = calRevenue(betterDelay, true, sol, clientsMap);

                if (rev2 > bestBandwidthRevenue) {
                    bestBandwidthRevenue = rev2;
                    bestBandwidthNodeID = nodeId;
                }

                sol.bandwidths.set(nodeId, originalBandwidth);
            }

            if (bestBandwidthNodeID != -1) {
                sol.bandwidths.set(bestBandwidthNodeID, sol.bandwidths.get(bestBandwidthNodeID) + 1);
                rev1 = bestBandwidthRevenue;
                clientsDelay = Simulator.run(this.graph, this.clients, sol);
                improvedBandwidth = true;
            }
        }

        return sol;
    }

    /**
     * @param clientList: list of client
     * @param clientsDelay: hashmap of clients and how long each client takes to reach destination with a finite bandwidth
     * @param paths: hashmap of clients and their respective shortest path with unlimited bandwidth
     * @return a sorted list of client id based on their beta and alpha values
     */
    private  ArrayList<Pair<Integer,Double>> sortByBetaAndAlpha(ArrayList<Client> clientList, HashMap<Integer, Integer> clientsDelay, HashMap<Integer, ArrayList<Integer>> paths) {
        ArrayList<Pair<Integer,Double>> clientsScore = new ArrayList<>();
        for (Client client : clientList) {
            double clientAlphaRequirement = client.alpha * (paths.get(client.id).size() - 1);
            double clientBetaRequirement = client.beta * (paths.get(client.id).size() - 1);
            //1. find score for each client
            double score;
            //Highest priority
            if (clientsDelay.get(client.id) > clientAlphaRequirement){
                score = 2.0 + ((double) clientsDelay.get(client.id)/clientAlphaRequirement);
                //Next highest priority
            } else if  (clientsDelay.get(client.id) > clientBetaRequirement){
                score = 1.0 + ((double) clientsDelay.get(client.id)/clientBetaRequirement);
            } else {
                if (clientBetaRequirement > 0) {
                    score = (double) clientsDelay.get(client.id) / clientBetaRequirement;
                } else {
                    score = 0.0;
                }
            }
            clientsScore.add(new Pair<>(client.id,score));
        }
        //2. sort client based on score
        clientsScore.sort((a, b) -> Double.compare(b.getSecond(),a.getSecond()));

        return clientsScore;
    }

    /**
     * @param clientsSorted: list of sorted clients by score
     * @param path: client paths
     * @return Hashmap of risk nodes and how many clients pass through the node
     */
    private HashMap<Integer,Integer> findRiskyNodes(ArrayList<Pair<Integer,Double>> clientsSorted, HashMap<Integer,ArrayList<Integer>> path,ArrayList<Integer> bandwidthList) {
        HashMap<Integer, Integer> nodeCount = new HashMap<>();
        HashMap<Integer,Integer> nodeMap = new HashMap<>();
        //1. finds the risky clients and finds their paths
        for (Pair<Integer, Double> pair : clientsSorted) {
            int clientId = pair.getFirst();
            ArrayList<Integer> pathOfClient = path.get(clientId);
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
            int nodeBandwidth = bandwidthList.get(nodeId);
            if (clientsVisited > nodeBandwidth) {
                nodeMap.put(nodeId, clientsVisited - nodeBandwidth);
            }
        }
        return nodeMap;
    }

    /**
     * @param nodeMap: hashmap of risky nodes and how many clients pass through it
     * @return sorted Arraylist of node by risk
     */
    private ArrayList<Integer> sortNodesByRisk (HashMap<Integer,Integer> nodeMap){
        ArrayList<Integer> nodesSorted = new ArrayList<>();
        for (Integer nodeId : nodeMap.keySet()){
            nodesSorted.add(nodeId);
        }
        nodesSorted.sort((x,y) -> Integer.compare(nodeMap.get(y),nodeMap.get(x)));
        return nodesSorted;
    }

    /**
     *  Compute the revenue based on if there is a lawsuit and/or a fine
     * @param clientsDelay hashmap of clients and their delays in their paths
     * @param bandwidthChanged boolean that tells Revenue if we change it
     * @param sol Solution Object
     * @return float of calculated Revenue
     */
    private float calRevenue(HashMap<Integer,Integer> clientsDelay, boolean bandwidthChanged, SolutionObject sol, HashMap<Integer,Client> clientMap) {
        int lawsuitThreshold = 0;
        int fccThreshold = 0;
        int fccClients = 0;
        //1. Check if there is a lawsuit
        for (Integer clientId : clientsDelay.keySet()) {
            Client client = clientMap.get(clientId);
            int delayTime = clientsDelay.get(clientId);
            int noDelayTime = sol.paths.get(clientId).size() - 1;

            if (delayTime > client.beta * noDelayTime) {
                lawsuitThreshold++;
            }
        }
        //2. Check if there is a fine
        for (Client client: clients) {
            if (client.isFcc){
                fccClients++;
                int delayTime = clientsDelay.get(client.id);
                int noDelayTime = sol.paths.get(client.id).size() - 1;

                if (delayTime > client.beta * noDelayTime) {
                    fccThreshold++;
                }
            }
        }

        boolean lawsuit = lawsuitThreshold >= Math.floor(info.rho1 * clients.size());
        boolean fine = fccThreshold >= Math.floor(info.rho2 * fccClients);

        return Revenue.revenue(info,sol,clientsDelay,lawsuit,fine,bandwidthChanged);
    }

    private ArrayList<Integer> alternativeBFS (Graph graph, int clientId, ArrayList<Integer> avoidedNodes){
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
                ArrayList<Integer> currentPath = new ArrayList<>();
                int x = clientId;
                while (x != -1){
                    currentPath.add(0, x);
                    x = parent.get(x);
                }
                return currentPath;
            }

            ArrayList<Integer> neighborsOfNode = graph.get(current);
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
}