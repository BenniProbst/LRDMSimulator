package org.lrdm.util;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.topologies.BalancedTreeTopologyStrategy;
import java.util.*;

/**
 * Erweiterte TreeNode-Klasse für Mirror-spezifische Funktionalitäten.
 * Verbindet TreeNode-Strukturen mit Mirror-Netzwerken.
 * 
 * @author Sebastian Götz <sebastian.goetz1@tu-dresden.de>
 */
public class MirrorNode extends TreeNode {
    private Mirror mirror;
    private Set<Link> links;
    private List<MirrorNode> mirrorNodeChildren; // Alle verbundenen Mirror-Knoten
    private int pendingLinks; // Geplante aber noch nicht erstellte Links
    
    public MirrorNode(int id, int depth) {
        super(id, depth);
        this.links = new HashSet<>();
        this.mirrorNodeChildren = new ArrayList<>();
        this.pendingLinks = 0;
    }
    
    public MirrorNode(int id, int depth, Mirror mirror) {
        this(id, depth);
        this.mirror = mirror;
    }
    
    /**
     * Gibt die aktuelle Anzahl der Target-Links zurück.
     * 
     * @return Anzahl der aktuellen Links
     */
    public int getNumTargetLinks() {
        if (mirror != null) {
            return mirror.getLinks().size();
        }
        return links.size();
    }
    
    /**
     * Berechnet die vorhergesagte Anzahl der Target-Links.
     * Berücksichtigt aktuelle und geplante Links.
     * 
     * @return Vorhergesagte Anzahl der Links
     */
    public int getPredictedNumTargetLinks() {
        return getNumTargetLinks() + pendingLinks;
    }
    
    /**
     * Gibt die Anzahl der verbundenen Mirror-Knoten zurück.
     * 
     * @return Anzahl der Mirror-Knoten
     */
    public int getNumMirrors() {
        return mirrorNodeChildren.size();
    }
    
    /**
     * Gibt eine Liste aller verbundenen Mirror-Knoten zurück.
     * 
     * @return Liste der Mirror-Knoten
     */
    public List<MirrorNode> getAllMirrors() {
        return new ArrayList<>(mirrorNodeChildren);
    }
    
    /**
     * Gibt alle Links dieses Knotens zurück.
     * 
     * @return Set der Links
     */
    public Set<Link> getAllLinks() {
        if (mirror != null) {
            return new HashSet<>(mirror.getLinks());
        }
        return new HashSet<>(links);
    }
    
    /**
     * Erstellt und verlinkt Mirrors basierend auf der BalancedTreeTopologyStrategy.
     * Dies ist die Hauptfunktion für die Integration mit der bestehenden Topology-Strategie.
     * 
     * @param mirrors Liste der zu verlinkenden Mirrors
     * @param simTime Aktuelle Simulationszeit
     * @param props Simulationseigenschaften
     * @return Set der erstellten Links
     */
    public Set<Link> createAndLinkMirrors(Network n, List<Mirror> mirrors, int simTime, Properties props) {
        if (mirrors.isEmpty()) return new HashSet<>();
        
        BalancedTreeTopologyStrategy strategy = new BalancedTreeTopologyStrategy();
        
        Mirror root = mirrors.get(0);
        List<Mirror> remainingMirrors = new ArrayList<>(mirrors.subList(1, mirrors.size()));
        
        Set<Link> createdLinks = strategy.initNetworkSub(n, root, remainingMirrors, simTime, props);
        
        // Aktualisiere interne Datenstrukturen
        updateInternalStructures(mirrors, createdLinks);
        
        return createdLinks;
    }

    /**
     * Aktualisiert die internen Datenstrukturen nach dem Erstellen von Links.
     */
    private void updateInternalStructures(List<Mirror> mirrors, Set<Link> createdLinks) {
        // Aktualisiere Links
        this.links.addAll(createdLinks);
        
        // Aktualisiere Mirror-Knoten
        for (Mirror m : mirrors) {
            MirrorNode node = findOrCreateMirrorNode(m);
            if (!mirrorNodeChildren.contains(node)) {
                mirrorNodeChildren.add(node);
            }
        }
        
        // Verteile Links auf die entsprechenden MirrorNodes
        for (Link link : createdLinks) {
            MirrorNode sourceNode = findMirrorNodeByMirror(link.getSource());
            MirrorNode targetNode = findMirrorNodeByMirror(link.getTarget());
            
            if (sourceNode != null) {
                sourceNode.addLink(link);
            }
            if (targetNode != null) {
                targetNode.addLink(link);
            }
        }
    }
    
    /**
     * Findet oder erstellt einen MirrorNode für ein gegebenes Mirror.
     */
    private MirrorNode findOrCreateMirrorNode(Mirror mirror) {
        for (MirrorNode node : mirrorNodeChildren) {
            if (node.getMirror() != null && node.getMirror().getID() == mirror.getID()) {
                return node;
            }
        }
        
        // Erstelle neuen MirrorNode wenn nicht gefunden
        MirrorNode newNode = new MirrorNode(mirror.getID(), 0, mirror);
        return newNode;
    }
    
    /**
     * Findet einen MirrorNode basierend auf einem Mirror.
     */
    private MirrorNode findMirrorNodeByMirror(Mirror mirror) {
        if (this.mirror != null && this.mirror.getID() == mirror.getID()) {
            return this;
        }
        
        for (MirrorNode node : mirrorNodeChildren) {
            if (node.getMirror() != null && node.getMirror().getID() == mirror.getID()) {
                return node;
            }
        }
        
        return null;
    }
    
    /**
     * Fügt einen Link zu diesem Knoten hinzu.
     * 
     * @param link Der hinzuzufügende Link
     */
    public void addLink(Link link) {
        links.add(link);
        if (mirror != null) {
            mirror.addLink(link);
        }
    }
    
    /**
     * Entfernt einen Link von diesem Knoten.
     * 
     * @param link Der zu entfernende Link
     */
    public void removeLink(Link link) {
        links.remove(link);
        if (mirror != null) {
            mirror.removeLink(link);
        }
    }
    
    /**
     * Fügt einen geplanten Link hinzu (erhöht pendingLinks).
     * 
     * @param count Anzahl der geplanten Links
     */
    public void addPendingLinks(int count) {
        this.pendingLinks += count;
    }
    
    /**
     * Bestätigt geplante Links (verringert pendingLinks).
     * 
     * @param count Anzahl der bestätigten Links
     */
    public void confirmPendingLinks(int count) {
        this.pendingLinks = Math.max(0, this.pendingLinks - count);
    }
    
    /**
     * Fügt einen MirrorNode zur Liste der verbundenen Mirrors hinzu.
     * 
     * @param mirrorNode Der hinzuzufügende MirrorNode
     */
    public void addMirrorNode(MirrorNode mirrorNode) {
        if (!mirrorNodeChildren.contains(mirrorNode)) {
            mirrorNodeChildren.add(mirrorNode);
        }
    }
    
    /**
     * Entfernt einen MirrorNode aus der Liste der verbundenen Mirrors.
     * 
     * @param mirrorNode Der zu entfernende MirrorNode
     */
    public void removeMirrorNode(MirrorNode mirrorNode) {
        mirrorNodeChildren.remove(mirrorNode);
    }
    
    /**
     * Gibt das zugeordnete Mirror zurück.
     * 
     * @return Das Mirror-Objekt oder null
     */
    public Mirror getMirror() {
        return mirror;
    }
    
    /**
     * Setzt das zugeordnete Mirror.
     * 
     * @param mirror Das zu setzende Mirror
     */
    public void setMirror(Mirror mirror) {
        this.mirror = mirror;
    }
    
    /**
     * Gibt die Anzahl der geplanten Links zurück.
     * 
     * @return Anzahl der geplanten Links
     */
    public int getPendingLinks() {
        return pendingLinks;
    }
    
    /**
     * Überprüft, ob dieser Knoten mit einem anderen MirrorNode verlinkt ist.
     * 
     * @param other Der andere MirrorNode
     * @return true wenn verlinkt
     */
    public boolean isLinkedWith(MirrorNode other) {
        if (mirror != null && other.getMirror() != null) {
            return mirror.isLinkedWith(other.getMirror());
        }
        
        // Überprüfe über die Links
        for (Link link : links) {
            if ((link.getSource().getID() == this.getId() && link.getTarget().getID() == other.getId()) ||
                (link.getSource().getID() == other.getId() && link.getTarget().getID() == this.getId())) {
                return true;
            }
        }
        
        return false;
    }
    
    @Override
    public void addChild(TreeNode child) {
        super.addChild(child);
        if (child instanceof MirrorNode) {
            addMirrorNode((MirrorNode) child);
        }
    }
    
    @Override
    public String toString() {
        return "MirrorNode{" +
                "id=" + getId() +
                ", depth=" + getDepth() +
                ", mirrorId=" + (mirror != null ? mirror.getID() : "null") +
                ", links=" + getNumTargetLinks() +
                ", pendingLinks=" + pendingLinks +
                ", children=" + getChildren().size() +
                '}';
    }
}