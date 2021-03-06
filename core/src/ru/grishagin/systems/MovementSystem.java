package ru.grishagin.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.gdx.ai.msg.Telegram;
import com.badlogic.gdx.ai.msg.Telegraph;
import com.badlogic.gdx.ai.pfa.Connection;
import com.badlogic.gdx.ai.pfa.PathSmoother;
import com.badlogic.gdx.ai.pfa.indexed.IndexedAStarPathFinder;
import com.badlogic.gdx.math.Vector2;
import ru.grishagin.components.DestinationComponent;
import ru.grishagin.components.NameComponent;
import ru.grishagin.components.PositionComponent;
import ru.grishagin.components.VelocityComponent;
import ru.grishagin.components.tags.DoorTag;
import ru.grishagin.components.tags.ImpassableComponent;
import ru.grishagin.components.tags.PlayerControlled;
import ru.grishagin.model.GameModel;
import ru.grishagin.model.map.TiledBasedMap;
import ru.grishagin.model.messages.MessageType;
import ru.grishagin.systems.patfinding.*;
import ru.grishagin.utils.Logger;

import static ru.grishagin.model.map.TiledBasedMap.ROOF_LAYER;

public class MovementSystem extends IteratingSystem implements Telegraph {
    private static final float STOP_PRECISION = 0.1f;

    private ComponentMapper<PositionComponent> pm = ComponentMapper.getFor(PositionComponent.class);
    private ComponentMapper<VelocityComponent> vm = ComponentMapper.getFor(VelocityComponent.class);
    private ComponentMapper<DestinationComponent> dm = ComponentMapper.getFor(DestinationComponent.class);

    TiledGraph<FlatTiledNode> mapGraph;
    TiledManhattanDistance<FlatTiledNode> heuristic;
    IndexedAStarPathFinder<FlatTiledNode> pathFinder;
    PathSmoother<FlatTiledNode, Vector2> pathSmoother;

    public MovementSystem(){
        super(Family.all(DestinationComponent.class, PositionComponent.class, VelocityComponent.class).get());
    }

    public void setMap(TiledBasedMap map){
        //convert map to graph
        mapGraph = new FlatTiledGraph();
        mapGraph.init(map);

        heuristic = new TiledManhattanDistance<FlatTiledNode>();
        //pathSmoother = new PathSmoother<FlatTiledNode, Vector2>(new TiledRaycastCollisionDetector<FlatTiledNode>());
        pathFinder = new IndexedAStarPathFinder<FlatTiledNode>(mapGraph, true);
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        PositionComponent position = pm.get(entity);
        VelocityComponent velocity = vm.get(entity);
        DestinationComponent destination = dm.get(entity);

        if(destination.path == null){
            TiledSmoothableGraphPath path = buildPath(position, destination);
            destination.path = path;
            Logger.info("Path for " + entity.getComponent(NameComponent.class) + " is built. Destination is " + destination.x + ", " + destination.y);
        }

        if(Math.abs(position.x - destination.x) < STOP_PRECISION && Math.abs(position.y - destination.y) < STOP_PRECISION){
            stop(entity);
        } else {
            followPath(entity);
        }

        position.x += velocity.x*deltaTime;
        position.y += velocity.y*deltaTime;

        if(entity.getComponent(PlayerControlled.class) != null){
            showHideRoof(position);
        }
    }

    private void followPath(Entity entity){
        PositionComponent position = pm.get(entity);
        VelocityComponent velocity = vm.get(entity);
        DestinationComponent destination = dm.get(entity);

        FlatTiledNode currentNode = mapGraph.getNode((int)position.x, (int)position.y);
        for (int i = 0; i < destination.path.nodes.size - 1; i++) { //entity cannot follow path if it has position == destination
            FlatTiledNode node = destination.path.nodes.get(i);
            //find node in the path where entity currently is standing on
            if(node.x == currentNode.x && node.y == currentNode.y){
                FlatTiledNode nextPathNode = destination.path.nodes.get(i + 1);

                float deltaX = Math.abs(nextPathNode.x - position.x);
                float deltaY = Math.abs(nextPathNode.y - position.y);
                float distance = (float)Math.sqrt(deltaX*deltaX + deltaY*deltaY);

                if(distance != 0) {
                    float sin, cos;
                    sin = deltaY / distance;
                    cos = deltaX / distance;

                    velocity.x = velocity.speed * cos;
                    velocity.y = velocity.speed * sin;

                    if (Math.abs(nextPathNode.x - position.x) > 0.1f) {
                        if (nextPathNode.x - position.x < 0) {
                            velocity.x = -velocity.x;
                        }
                    } else {
                        position.x = nextPathNode.x;
                        velocity.x = 0;
                    }

                    if (Math.abs(nextPathNode.y - position.y) > 0.1f) {
                        if (nextPathNode.y - position.y < 0) {
                            velocity.y = -velocity.y;
                        }
                    } else {
                        position.y = nextPathNode.y;
                        velocity.y = 0;
                    }
                }
            }
        }
    }

    private void stop(Entity entity){
        entity.remove(DestinationComponent.class);
        VelocityComponent velocity = vm.get(entity);
        velocity.x = 0;
        velocity.y = 0;
    }

    private TiledSmoothableGraphPath buildPath(PositionComponent position, DestinationComponent destination){
        FlatTiledNode startNode = mapGraph.getNode((int)position.x, (int)position.y);
        FlatTiledNode endNode = mapGraph.getNode((int)destination.x, (int)destination.y);

        TiledSmoothableGraphPath<FlatTiledNode> path = new TiledSmoothableGraphPath<FlatTiledNode>();

        //if end node is unavailable, build path for the closest available
        if(endNode.getType() == TileNodeType.IMPASSABLE){
            mapGraph.changeNodeType(endNode.getIndex(), TileNodeType.NORMAL);
            pathFinder.searchNodePath(startNode, endNode, heuristic, path);
            endNode = path.nodes.get(path.nodes.size - 2);//previous before last (impassable) node
            mapGraph.changeNodeType(mapGraph.getNode((int)destination.x, (int)destination.y).getIndex(), TileNodeType.IMPASSABLE);//change type back
            path.clear();

            destination.x = endNode.x;
            destination.y = endNode.y;
        }

        pathFinder.searchNodePath(startNode, endNode, heuristic, path);
        return path;
    }

    //roof layer has vertical offset
    private void showHideRoof(PositionComponent playerPosition){
        TiledBasedMap currentMap = GameModel.instance.getCurrentMap();
        if(currentMap.hasObject(playerPosition.x, playerPosition.y, ROOF_LAYER)){
            currentMap.setLayerVisibility(ROOF_LAYER, false);
        } else {
            currentMap.setLayerVisibility(ROOF_LAYER, true);
        }
    }

    @Override
    public boolean handleMessage(Telegram msg) {
        if(msg.extraInfo != null){
            Entity entity = (Entity)msg.extraInfo;
            PositionComponent position = pm.get(entity);
            FlatTiledNode node = mapGraph.getNode((int)position.x, (int)position.y);

            switch (msg.message){
                case MessageType.CLOSED:
                case MessageType.OPENED:
                    ImpassableComponent impassableComponent = entity.getComponent(ImpassableComponent.class);
                    DoorTag isDoor = entity.getComponent(DoorTag.class);
                    if(isDoor != null){//make sure it is a door
                        if(impassableComponent == null){
                            entity.add(new ImpassableComponent());
                            mapGraph.changeNodeType(node.getIndex(), TileNodeType.IMPASSABLE);
                        } else {
                            entity.remove(ImpassableComponent.class);
                            mapGraph.changeNodeType(node.getIndex(), TileNodeType.NORMAL);
                        }
                    }
                    break;
            }
        }
        return true;
    }
}
