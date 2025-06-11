package org.lrdm.topologies.base;

import org.graphstream.ui.swing.util.AttributeUtils;
import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;

import java.util.*;

public abstract class BuildAsSubstructure extends TopologyStrategy {

    Map<Mirror, BuildAsSubstructure> mirrorToConnectedMirrors = new HashMap<>();
    Set<Mirror> objectMirror = new HashSet<>();

    public void addPortObjectMirror(Mirror m, BuildAsSubstructure b) {
        mirrorToConnectedMirrors.put(m, b);
        if(!objectMirror.contains(m)) {
            b.objectMirror.add(m);
        }
    }

    public BuildAsSubstructure getPortObjectMirror(Mirror m) {
        return mirrorToConnectedMirrors.get(m);
    }

    public boolean maybeDeletePortObjectMirror(Mirror m) {
        objectMirror.remove(m);
        return mirrorToConnectedMirrors.remove(m) != null;
    }

    public boolean addPortObjectMirror(Mirror m) {
        return objectMirror.add(m);
    }

    public Set<Mirror> getPortObjectMirrors() {
        return objectMirror;
    }

    public abstract Set<Link> initNetworkSub(Network n, Mirror root, List<Mirror> mirrorsToConnect, int simTime, Properties props);
    public void restartNetworkSub(Network n, Mirror root, Properties props, int simTime) {
        AttributeUtils.Tuple<Set<Mirror>,Set<Link>> gatheredSubstructures = gatherSubstructures();
        Set<Mirror> mirrors = gatheredSubstructures.x;
        Set<Link> links = gatheredSubstructures.y;

        //remove links from
        for(Link l : links) {
            if(mirrors.contains(l.getSource()) && mirrors.contains(l.getTarget())) {
                n.getLinks().remove(l);
            }
        }

        for(Mirror m : mirrors) {
            m.getLinks().removeIf(l -> mirrors.contains(l.getSource()) && mirrors.contains(l.getTarget()));
        }

        initNetworkSub(n, root, new ArrayList<>(n.getMirrors()), simTime, props);
    }
    public abstract void handleAddNewMirrorsSub(Network n, int newMirrors, Properties props, int simTime);
    public abstract void handleRemoveMirrorsSub(Network n, int removeMirrors, Properties props, int simTime);
    public abstract int getNumTargetLinksSub(Network n);
    public abstract int getPredictedNumTargetLinksSub(Action a);

    public Set<Mirror> shutdownSubstructures(int simTime){
        Set<Mirror> removed = new HashSet<>(objectMirror);
        for(BuildAsSubstructure b : mirrorToConnectedMirrors.values()) {
            removed.addAll(b.shutdownSubstructures(simTime));
        }
        for(Mirror m : objectMirror) {
            m.shutdown(simTime);
        }
        return removed;
    }

    public AttributeUtils.Tuple<Set<Mirror>,Set<Link>> gatherSubstructures(){
        Set<Link> links = new HashSet<>();
        Set<Mirror> mirrors = new HashSet<>();

        for(BuildAsSubstructure b : mirrorToConnectedMirrors.values()) {
            AttributeUtils.Tuple<Set<Mirror>,Set<Link>> t = b.gatherSubstructures();
            mirrors.addAll(t.x);
            links.addAll(t.y);
        }

        for(Mirror m : objectMirror) {
            if(m != objectMirror.iterator().next()){
                links.addAll(m.getLinks());
                mirrors.add(m);
            }
        }

        return new AttributeUtils.Tuple<>(mirrors,links);
    }

}