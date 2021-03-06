package ru.grishagin.view;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.maps.MapLayer;
import com.badlogic.gdx.maps.MapObject;;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.TiledMapTileLayer;
import com.badlogic.gdx.maps.tiled.renderers.IsometricTiledMapRenderer;
import com.badlogic.gdx.math.Vector2;
import ru.grishagin.components.PositionComponent;
import ru.grishagin.components.ShaderComponent;
import ru.grishagin.components.SpriteComponent;
import ru.grishagin.utils.AssetManager;
import ru.grishagin.utils.ShaderHelper;

import java.util.*;

import static ru.grishagin.model.map.TiledBasedMap.TILE_HEIGHT;
import static ru.grishagin.model.map.TiledBasedMap.TILE_WIDTH;

public class ExtendedIsometricTiledMapRenderer extends IsometricTiledMapRenderer {
    private ComponentMapper<SpriteComponent> sm = ComponentMapper.getFor(SpriteComponent.class);
    private ComponentMapper<PositionComponent> pm = ComponentMapper.getFor(PositionComponent.class);
    private ComponentMapper<ShaderComponent> shm = ComponentMapper.getFor(ShaderComponent.class);

    private DepthComparator<Entity> comparator;

    private List<Entity> spriteEntities = new ArrayList<>();

    public ExtendedIsometricTiledMapRenderer(TiledMap map) {
        super(map);
        comparator = new DepthComparator<>();
    }

    public void setSpriteEntities(ImmutableArray<Entity> spriteEntities) {
        this.spriteEntities = Arrays.asList(spriteEntities.toArray());
        Collections.sort(this.spriteEntities, comparator);
    }

    @Override
    public void render() {
        beginRender();
        int currentLayer = 0;
        for (MapLayer layer : map.getLayers()) {
            if (layer.isVisible()) {
                renderMapLayer(layer);
                currentLayer++;
                for (Entity entity : spriteEntities) {
                    if(currentLayer == sm.get(entity).renderingOrder){
                        drawSprite(sm.get(entity), pm.get(entity), shm.get(entity));
                    }
                }
            }
        }
        endRender();
    }

    private void drawSprite(SpriteComponent spriteComponent, PositionComponent position, ShaderComponent shaderComponent){
        int tileHeight = (int)map.getProperties().get(TILE_HEIGHT);
        Vector2 renderPosition = new Vector2();
        float x = position.x;
        float y = position.y;
        float posY = - x * tileHeight;
        float posX =  y * tileHeight;
        renderPosition.x = (posX - posY);
        renderPosition.y = (posX + posY) / 2;

        //FOR DEVELOPMENT ONLY!
        Sprite s = new Sprite(AssetManager.instance.getTexture("tiles/red.png"));
        s.setAlpha(0.5f);
        //batch.draw(s, renderPosition.x, renderPosition.y);
        ///

        batch.end();

        //TODO: setting the same shader every time can affect performance
        if(shaderComponent != null && shaderComponent.shaderType != null && shaderComponent.isActive) {
            //get shader with properties
            ShaderProgram shader = ShaderHelper.getShader(shaderComponent, spriteComponent);
            getBatch().setShader(shader);
        } else {
            //setup default shader
            batch.setShader(null);
        }

        spriteComponent.sprite.setPosition(renderPosition.x + spriteComponent.offset.x,
                renderPosition.y + spriteComponent.offset.y);

        batch.begin();

        this.batch.draw(spriteComponent.sprite, spriteComponent.sprite.getX(), spriteComponent.sprite.getY());
        /*this.batch.draw(spriteComponent.sprite, renderPosition.x + spriteComponent.offset.x,
                renderPosition.y + spriteComponent.offset.y);*/

        getBatch().setShader(null);
    }
}
