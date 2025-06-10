package org.lrdm.topologies.base;

import org.lrdm.Link;
import org.lrdm.Mirror;
import org.lrdm.Network;
import org.lrdm.effectors.Action;

import java.util.*;

public abstract class BuildAsSubstructure {

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

    public abstract Set<Link> initNetworkSub(Network n, Mirror root, List<Mirror> mirrorsToConnect, Properties props);
    public abstract void restartNetworkSub(Network n, Mirror root, Properties props, int simTime);
    public abstract void handleAddNewMirrorsSub(Network n, int newMirrors, Properties props, int simTime);
    public abstract void handleRemoveMirrorsSub(Network n, int removeMirrors, Properties props, int simTime);
    public abstract int getNumTargetLinksSub(Network n);
    public abstract int getPredictedNumTargetLinksSub(Action a);

    public Set<Mirror> removeSubstructure(int simTime){
        Set<Mirror> removed = new HashSet<>(objectMirror);
        for(BuildAsSubstructure b : mirrorToConnectedMirrors.values()) {
            removed.addAll(b.removeSubstructure(simTime));
        }
        for(Mirror m : objectMirror) {
            m.shutdown(simTime);
        }
        return removed;
    }

}