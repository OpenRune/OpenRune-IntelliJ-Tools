package dev.openrune.config

import dev.openrune.definition.codec.*
import kotlin.reflect.KClass

enum class ConfigType(val header : String,val codec: KClass<*>) {
    ITEM("item",ItemCodec::class),
    OBJECT("object", ObjectCodec::class),
    SPOTANIM("graphics",SpotAnimCodec::class),
    SEQUENCE("animation",SequenceCodec::class),
    STRUCT("struct",StructCodec::class),
    NPC("npc",NPCCodec::class),
    ENUM("enum",EnumCodec::class),
    VARBIT("varbit",VarBitCodec::class),
    AREA("area", AreaCodec::class),
    HEALTHBAR("health",HealthBarCodec::class),
    HITSPLAT("hitsplat",HitSplatCodec::class),
    IDENTKIT("idk",IdentityKitCodec::class),
    INV("inventory",InventoryCodec::class),
    OVERLAY("overlay",OverlayCodec::class),
    UNDERLAY("underlay",UnderlayCodec::class),
    PARAMS("params",ParamCodec::class),
    VARP("varp",VarBitCodec::class),
    VARCLIENT("varclient",VarClientCodec::class);

    companion object {
        fun forHeader(header : String) = values().find { it.header.equals(header, true) }
    }

}