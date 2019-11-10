package ru.grishagin.entities;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.maps.MapProperties;
import com.badlogic.gdx.maps.objects.RectangleMapObject;
import com.badlogic.gdx.maps.tiled.TiledMap;
import com.badlogic.gdx.maps.tiled.objects.TiledMapTileMapObject;
import com.badlogic.gdx.math.Vector2;
import com.fasterxml.jackson.databind.ObjectMapper;
import ru.grishagin.components.*;
import ru.grishagin.components.ai.AgentComponent;
import ru.grishagin.components.items.ArmorComponent;
import ru.grishagin.components.items.WeaponComponent;
import ru.grishagin.components.stats.*;
import ru.grishagin.components.tags.DoorTag;
import ru.grishagin.components.tags.HostileTag;
import ru.grishagin.components.tags.ImpassableComponent;
import ru.grishagin.components.tags.PlayerControlled;
import ru.grishagin.model.GameModel;
import ru.grishagin.model.actions.Action;
import ru.grishagin.model.actions.OpenAction;
import ru.grishagin.model.actions.TransferAction;
import ru.grishagin.model.map.TiledMapHelper;
import ru.grishagin.utils.AssetManager;
import ru.grishagin.utils.Logger;

import java.io.IOException;
import java.util.*;

public class EntityFactory {

    private static final String NAME = "name";
    private static final String ACTION = "action";
    private static final String INVENTORY = "inventory";
    private static final String HOSTILE = "hostile";
    private static final String HEALTH = "health";
    private static final String SKILLS = "skills";
    private static final String WEAPON = "weapon";
    private static final String ARMOR = "armor";
    private static final String LOOT = "loot";
    private static final String ITEMS = "items";
    private static final String IMPASSABLE = "impassable";
    private static final String CLOSED = "closed";
    private static final String DOOR = "door";
    private static final String STATE_IDS = "stateIds";
    private static final String GID = "gid";
    private static final String WIDTH = "width";
    private static final String HEIGHT = "height";
    private static final String ID = "id";
    public static final String X = "x";
    public static final String Y = "y";
    public static final String NPC_ID = "npcId";

    private static final String TRANSFER_ACTION = "transfer";
    private static final String OPEN_ACTION = "open";

    private static final int DEFAULT_MAX_WEIGHT = 100;

    private static ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);

    public static Entity makePlayer(Vector2 position){
        Entity entity = new Entity();
        entity.add(new NameComponent("Player"));
        entity.add(new PlayerControlled());
        entity.add(new PositionComponent(position));
        entity.add(new VelocityComponent(5f));
        entity.add(new InventoryComponent(100000));

        entity.add(new HealthComponent(100));
        entity.add(new HungerComponent());
        entity.add(new ThirstComponent());
        entity.add(new FatigueComponent());
        entity.add(new RadDoseComponent());
        entity.add(new ToxicityDoseComponent());

        entity.add(new CombatSkillsComponent()
                .setSkill(WeaponComponent.DamageType.MELEE, 50)
                .setSkill(WeaponComponent.DamageType.FIREARM, 10));

        Entity defaultWeapon = new Entity();
        defaultWeapon.add(new WeaponComponent(WeaponComponent.DamageType.MELEE, 4, 1, 1));
        defaultWeapon.add(new SoundComponent(Collections.singletonMap("3", "weapon-swing6")));
        entity.add(new EquippedWeaponComponent(defaultWeapon));
        entity.add(new EquippedArmorComponent());//empty armor, 0 defence

        Sprite sprite = new Sprite(AssetManager.instance.getTexture("player/0.png"));
        sprite.setSize(32, 32);
        SpriteComponent spriteComponent = new SpriteComponent(sprite);
        spriteComponent.offset.x = 8;
        spriteComponent.offset.y = 8;
        entity.add(spriteComponent);

        //entity.add(new ShaderComponent(ShaderType.OUTLINE));

        return entity;
    }

    public static Entity spawn(RectangleMapObject spawner, TiledMap map){
        int id = (int)spawner.getProperties().get(NPC_ID);
        Vector2 position = TiledMapHelper.convertObjectMapCoordsToInternal(spawner, map);
        if(id == -1){
            return makePlayer(position);
        } else {
            return makeNPC(id, position);
        }
    }

    public static Entity makeNPC(int id, Vector2 position){
        Entity npc = new Entity();

        npc.add(new TypeIdComponent(id));
        npc.add(new PositionComponent(position));
        npc.add(new SpriteComponent(new Sprite(AssetManager.instance.getNPCImage(id))));
        npc.add(new ImpassableComponent());

        Map<String, Object> npcConfig = AssetManager.instance.readFromJson(AssetManager.NPC).get(String.valueOf(id));
        for (String componentName : npcConfig.keySet()) {
            Component component = makeComponent(componentName, npcConfig.get(componentName));
            npc.add(component);
        }

        npc.add(new VelocityComponent(5f));

        if(npc.getComponent(HostileTag.class) != null){
            npc.add(new ShaderComponent(ShaderType.OUTLINE, Collections.singletonMap(ShaderComponent.COLOR, Color.RED)));
            npc.add(new AgentComponent());//give it AI
        }

        return npc;
    }

    /*public static Entity makeNPC(){
        Entity entity = makeBasicNPC();

        entity.add(new SpriteComponent(new Sprite(AssetManager.instance.getTexture("npc/rathound.png"))));
        entity.add(new HostileTag());

        Entity defaultWeapon = new Entity();
        defaultWeapon.add(new WeaponComponent(WeaponComponent.DamageType.MELEE, 1, 1, 1));
        entity.add(new EquippedWeaponComponent(defaultWeapon));

        Entity suit = new Entity();
        suit.add(new ArmorComponent(ArmorComponent.ArmorType.SUIT, 1, 0));
        EquippedArmorComponent equippedArmor = new EquippedArmorComponent();
        equippedArmor.changeSuit(suit);
        entity.add(equippedArmor);//empty armor, 0 defence

        return postProcessNPC(entity);
    }

    private static Entity makeBasicNPC(){
        Entity entity = new Entity();

        entity.add(new NameComponent("Rat"));
        entity.add(new PositionComponent(10, 10));
        entity.add(new HealthComponent(15));
        entity.add(new ImpassableComponent());
        entity.add(new InventoryComponent(10));

        return entity;
    }

    private static Entity postProcessNPC(Entity entity){
        if(entity.getComponent(HostileTag.class) != null){
            entity.add(new ShaderComponent(ShaderType.OUTLINE));
        } else if(entity.getComponent(InteractiveComponent.class) != null){
            entity.add(new ShaderComponent(ShaderType.OUTLINE)); //TODO: add yellow outline
        }

        return entity;
    }*/

    public static Entity makeChest(int x, int y){
        Entity entity = new Entity();
        entity.add(new PositionComponent(x, y));
        entity.add(new SpriteComponent(new Sprite(
                AssetManager.instance.getTexture("tiles/grassland_tiles.png"), 0, 284, 64, 32)));
        entity.add(new InteractiveComponent(new TransferAction()));
        entity.add(new InventoryComponent(1000));
        entity.add(new ImpassableComponent());

        return entity;
    }

    public static Entity makeEntity(MapObject object, TiledMap map){
        Entity entity = new Entity();

        Vector2 position = TiledMapHelper.convertObjectMapCoordsToInternal(object, map);
        entity.add(new PositionComponent(position));

        //make components from specific properties
        fillComponents(entity, object.getProperties());

        if(object instanceof TiledMapTileMapObject) {
            //make components from properties of typed tiles
            fillComponents(entity, ((TiledMapTileMapObject)object).getTile().getProperties());

            //extract texture from tile directly
            entity.add(new SpriteComponent(new Sprite(((TiledMapTileMapObject)object).getTextureRegion())));

            //mark enemies with red outline and interactive element with yellow
            if(entity.getComponent(HostileTag.class) != null) {
                entity.add(new ShaderComponent(ShaderType.OUTLINE, Collections.singletonMap(ShaderComponent.COLOR, Color.RED)));
            } else if(entity.getComponent(InteractiveComponent.class) != null){
                entity.add(new ShaderComponent(ShaderType.OUTLINE, Collections.singletonMap(ShaderComponent.COLOR, Color.YELLOW)));
            }
        }

        //fill inventory with specified items
        if (im.get(entity) != null && object.getProperties().containsKey(ITEMS)){
            fillWithItems(entity, ((String)object.getProperties().get(ITEMS)).split(","));
        }

        return entity;
    }

    private static void fillComponents(Entity entity, MapProperties properties){
        Iterator<String> iterator = properties.getKeys();
        while (iterator.hasNext()){
            String property = iterator.next();
            Component component = makeComponent(property, properties.get(property));
            if(component != null) {
                entity.add(component);
            }
        }
    }

    private static Component makeComponent(String componentName, Object rawComponentData){
        switch (componentName) {
            case NAME:
                return new NameComponent((String) rawComponentData);
            case ACTION:
                return new InteractiveComponent(makeAction((String) rawComponentData));
            case INVENTORY:
                return new InventoryComponent((int)rawComponentData);
            case HOSTILE:
                return new HostileTag();
            case HEALTH:
                return new HealthComponent((int)rawComponentData);
            case SKILLS:
                Map<String, Integer> skillsData = (Map<String, Integer>)rawComponentData;
                CombatSkillsComponent combatSkillsComponent = new CombatSkillsComponent();
                for (String skillName : skillsData.keySet()) {
                    combatSkillsComponent.setSkill(WeaponComponent.DamageType.valueOf(skillName.toUpperCase()),
                            skillsData.get(skillName));
                }
                return combatSkillsComponent;
            case WEAPON:
                return new EquippedWeaponComponent(ItemFactory.getItem((int)rawComponentData));
            case ARMOR: //armor is stored as array of ids of armor items
                Entity suit = null;
                Entity head = null;
                for (Integer armorId : (List<Integer>) rawComponentData) {//iterate over all listed armors
                    Entity armorItem = ItemFactory.getItem(armorId);
                    if(armorItem.getComponent(ArmorComponent.class).type == ArmorComponent.ArmorType.SUIT){
                        if(suit != null){
                            Logger.warning("Suit already exists! It is overridden by " + armorId);
                        }
                        suit = armorItem;
                    } else if(armorItem.getComponent(ArmorComponent.class).type == ArmorComponent.ArmorType.HEAD){
                        if(head != null){
                            Logger.warning("Head already exists! It is overridden by " + armorId);
                        }
                        head = armorItem;
                    } else {
                        Logger.warning(armorId + " is not suit or head!");
                    }
                }

                //if armor element is not specified or some errors occured, create an empty armor element
                if(suit == null){
                    suit = new Entity();
                    suit.add(new ArmorComponent(ArmorComponent.ArmorType.SUIT, 0, 0));
                }

                if(head == null){
                    head = new Entity();
                    head.add(new ArmorComponent(ArmorComponent.ArmorType.HEAD, 0, 0));
                }

                return new EquippedArmorComponent(suit, head);
            case LOOT:
                InventoryComponent inventory = new InventoryComponent(DEFAULT_MAX_WEIGHT);
                Map<String, Integer> lootIds = (Map<String, Integer>)rawComponentData;
                for (String lootId : lootIds.keySet()) {
                    inventory.items.add(ItemFactory.getItem(Integer.valueOf(lootId), lootIds.get(lootId)));
                }
                return inventory;
            case ITEMS:
                return null;//items is not a component, but in the same property map
            case IMPASSABLE:
                return new ImpassableComponent();
            case CLOSED:
                return new ClosedComponent(true);
            case DOOR:
                return new DoorTag();
            case STATE_IDS:
                String convertedJSON = ((String)rawComponentData).replaceAll("&quot;", "\""); //return normal quotes to json string
                NextStatesIds nextStatesIds;
                try{
                    ObjectMapper mapper = new ObjectMapper();
                    nextStatesIds = new NextStatesIds(mapper.readValue(convertedJSON, HashMap.class));
                } catch (IOException e){
                    Logger.warning("Can't parse " + rawComponentData + ":\n" + e.toString());
                    nextStatesIds = new NextStatesIds();
                }
                return nextStatesIds;
            case GID:
                return new TileGIdComponent((int)rawComponentData);
            //skip some properties from tmx map
            case ID:
            case WIDTH:
            case HEIGHT:
            case X:
            case Y:
                return null;
            default:
                Logger.warning("Cannot create component \"" + componentName + "\"!");
                return null;
        }
    }

    private static Action makeAction(String actionName){
        switch (actionName) {
            case TRANSFER_ACTION:
                return new TransferAction();
            case OPEN_ACTION:
                return new OpenAction();
            default:
                System.out.println("Warning! Cannot create action \"" + actionName + "\"!");
                return null;
        }
    }

    private static void fillWithItems(Entity entity, String[] itemIds){
        for (String itemId : itemIds) {
            try {
                GameModel.instance.inventorySystem.addItem(entity, ItemFactory.getItem(Integer.parseInt(itemId)));
            } catch (NumberFormatException e){
                System.out.println("Error! Wrong format of item id: " + itemId);
            }
        }
    }
}
