package ru.grishagin.view;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import ru.grishagin.components.PositionComponent;
import ru.grishagin.components.TextureComponent;
import ru.grishagin.model.map.Map;
import ru.grishagin.utils.AssetManager;

import java.util.ArrayList;
import java.util.List;

public class RenderingEngine extends ViewMap{
    ComponentMapper<TextureComponent> tm = ComponentMapper.getFor(TextureComponent.class);
    ComponentMapper<PositionComponent> pm = ComponentMapper.getFor(PositionComponent.class);

    private SpriteBatch batch;
    private Map map;
    private Engine engine;
    private Camera camera;

    private boolean isoMode = true;

    public RenderingEngine(SpriteBatch batch, Map map, Engine engine) {
        super(map, batch);
        this.batch = batch;
        this.map = map;
        this.engine = engine;
    }

    @Override
    public void draw(OrthographicCamera camera){
        this.camera = camera;

        for (int i = 0; i < map.getxSize() ; i++) {
            for (int j = map.getySize() - 1; j >= 0; j--) {
                drawCell(getTexturesOnTile(i, j),i, j);
            }
        }
    }

    //sort and return all drawable objects is cell
    private List<TextureRegion> getTexturesOnTile(int x, int y){
        List<TextureRegion> textures = new ArrayList<>();

        textures.add(AssetManager.instance.getTileTexture(map.getCell(x, y).getTypeId())); //basic tile texture

        ImmutableArray<Entity> allDrawables = engine.getEntitiesFor(Family.all(TextureComponent.class, PositionComponent.class).get());
        for (int i = 0; i < allDrawables.size(); i++) {
            Entity entity = allDrawables.get(i);
            if((int)pm.get(entity).x == x && (int)pm.get(entity).y == y){
                textures.add(tm.get(entity).region);
            }
        }

        return textures;
    }

    private void drawCell(List<TextureRegion> textures, int x, int y){
        Vector3 renderPosition = new Vector3();
        if(isoMode) {//Isometric view
            int posY = -(int) (x * 32);
            int posX = (int) (y * 32);
            renderPosition.x = (posX - posY);
            renderPosition.y = (posX + posY) / 2;
        } else {//non-isometric view
            renderPosition.x = x;
            renderPosition.y = y;
        }

        //update render position from camera
        batch.setProjectionMatrix(camera.combined);

        batch.begin();
        for (int i = 0; i < textures.size(); i++) {
            batch.draw(textures.get(i), renderPosition.x, renderPosition.y);
        }
        batch.end();
    }
}
