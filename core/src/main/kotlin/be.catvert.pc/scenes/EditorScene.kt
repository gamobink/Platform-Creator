package be.catvert.pc.scenes

import be.catvert.pc.GameKeys
import be.catvert.pc.Log
import be.catvert.pc.PCGame
import be.catvert.pc.eca.Entity
import be.catvert.pc.eca.Prefab
import be.catvert.pc.eca.Tags
import be.catvert.pc.eca.components.Components
import be.catvert.pc.eca.components.RequiredComponent
import be.catvert.pc.eca.components.graphics.AtlasComponent
import be.catvert.pc.eca.containers.EntityContainer
import be.catvert.pc.eca.containers.Level
import be.catvert.pc.factories.PrefabFactory
import be.catvert.pc.factories.PrefabSetup
import be.catvert.pc.factories.PrefabType
import be.catvert.pc.managers.MusicManager
import be.catvert.pc.managers.ResourceManager
import be.catvert.pc.serialization.SerializationFactory
import be.catvert.pc.ui.Description
import be.catvert.pc.ui.ImGuiHelper
import be.catvert.pc.utility.*
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Circle
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.math.Vector3
import glm_.func.common.clamp
import glm_.func.common.min
import glm_.vec2.Vec2
import glm_.vec4.Vec4
import imgui.*
import imgui.functionalProgramming.mainMenuBar
import imgui.functionalProgramming.menu
import imgui.functionalProgramming.menuItem
import ktx.app.use
import ktx.assets.toAbsoluteFile
import ktx.assets.toLocalFile
import org.lwjgl.system.MemoryStack.stackPush
import org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog
import kotlin.math.roundToInt
import kotlin.reflect.full.findAnnotation

/**
 * Scène de l'éditeur de niveau
 */
class EditorScene(val level: Level, applyMusicTransition: Boolean) : Scene(level.background, level.backgroundColor) {
    private enum class ResizeMode(val resizeName: String) {
        FREE("Libre"), PROPORTIONAL("Proportionnel")
    }

    private enum class SelectGOMode {
        NO_MODE, MOVE, HORIZONTAL_RESIZE_LEFT, HORIZONTAL_RESIZE_RIGHT, VERTICAL_RESIZE_BOTTOM, VERTICAL_RESIZE_TOP, DIAGONALE_RESIZE
    }

    private data class GridMode(var active: Boolean = false, var offsetX: Int = 0, var offsetY: Int = 0, var cellWidth: Int = 50, var cellHeight: Int = 50) {
        fun putEntity(walkRect: Rect, point: Point, entity: Entity, level: Level): Boolean {
            getRectCellOf(walkRect, point)?.apply {
                if (walkRect.contains(this, true) && level.getAllEntitiesInCells(walkRect).none { it.box == this }) {
                    entity.box = this
                    return true
                }
            }
            return false
        }

        fun getRectCellOf(walkRect: Rect, point: Point): Rect? {
            var rect: Rect? = null
            walkCells(walkRect) {
                if (it.contains(point))
                    rect = it
            }
            return rect
        }

        fun walkCells(walkRect: Rect, walk: (cellRect: Rect) -> Unit) {
            for (x in (walkRect.x.roundToInt() + offsetX)..(walkRect.x.roundToInt() + walkRect.width + offsetX) step cellWidth) {
                for (y in (walkRect.y.roundToInt() + offsetY)..(walkRect.y.roundToInt() + walkRect.height + offsetY) step cellHeight) {
                    walk(Rect(x.toFloat(), y.toFloat(), cellWidth, cellHeight))
                }
            }
        }
    }

    /**
     * Classe de donnée permettant de créer le box de sélection
     */
    private data class SelectRectangleData(var startPosition: Point, var endPosition: Point, var rectangleStarted: Boolean = false) {
        fun getRect(): Rect {
            val minX = Math.min(startPosition.x, endPosition.x)
            val minY = Math.min(startPosition.y, endPosition.y)
            val maxX = Math.max(startPosition.x, endPosition.x)
            val maxY = Math.max(startPosition.y, endPosition.y)

            return Rect(minX, minY, (maxX - minX).roundToInt(), (maxY - minY).roundToInt())
        }
    }

    class EditorSceneUI(background: Background?) {
        enum class EditorMode(val modeName: String) {
            NO_MODE("Aucun"), SELECT("Sélection"), COPY("Copie"), SELECT_POINT("Sélection d'un point"), SELECT_GO("Sélection d'une entité"), TRY_LEVEL("Essai du niveau")
        }

        var editorMode = EditorMode.NO_MODE
        var entityAddStateComboIndex = 0
        var entityAddComponentComboIndex = 0

        val onSelectPoint = Signal<Point>()
        val onSelectGO = Signal<Entity?>()

        var entityCurrentStateIndex = 0

        var entityCurrentComponentIndex = 0

        val settingsLevelStandardBackgroundIndex = intArrayOf(-1)
        val settingsLevelParallaxBackgroundIndex = intArrayOf(-1)

        val settingsLevelBackgroundType: ImGuiHelper.Item<Enum<*>> = ImGuiHelper.Item(background?.type
                ?: BackgroundType.None)

        var showExitWindow = false

        var showInfoEntityWindow = false
        var showInfoEntityTextWindow = false
        var showInfoPrefabWindow = false

        var prefabInfoPrefabWindow: Prefab? = null

        var addTagPopupNameBuffer = ""

        var showEntitiesWindow = true
        var entitiesTypeIndex = 0
        var entitiesShowOnlyUser = false
        var entitiesSpritePackIndex = 0
        var entitiesSpritePackTypeIndex = 0
        var entitiesSpritePhysics = true
        var entitiesSpriteRealSize = false
        var entitiesSpriteCustomSize = Size(50, 50)

        var godModeTryMode = false

        var createStateInputText = "Nouveau état"
        var createPrefabInputText = "Nouveau prefab"

        val menuBarHeight = (g.fontBaseSize + g.style.framePadding.y * 2f).roundToInt()

        init {
            when (background?.type) {
                BackgroundType.Standard -> settingsLevelStandardBackgroundIndex[0] = PCGame.standardBackgrounds().indexOfFirst { it.backgroundFile == (background as StandardBackground).backgroundFile }
                BackgroundType.Parallax -> settingsLevelParallaxBackgroundIndex[0] = PCGame.parallaxBackgrounds().indexOfFirst { it.parallaxDataFile == (background as ParallaxBackground).parallaxDataFile }
                BackgroundType.None -> {
                }
            }
        }
    }

    override var entityContainer: EntityContainer = level

    private val shapeRenderer = ShapeRenderer().apply { setAutoShapeType(true) }

    private val editorFont = BitmapFont(Constants.editorFontPath)

    private val cameraMoveSpeed = 10f

    private val gridMode = GridMode()

    private var selectEntityMode = SelectGOMode.NO_MODE
    private var resizeEntityMode = ResizeMode.PROPORTIONAL

    private val selectEntities = mutableSetOf<Entity>()
    private var selectEntity: Entity? = null

    private var selectEntityTryMode: Entity? = null

    private var selectLayer = 0

    /**
     * Est-ce que à la dernière frame, la bouton gauche était pressé
     */
    private var latestLeftButtonClick = false

    /**
     * Est-ce que à la dernière frame, la bouton droit était pressé
     */
    private var latestRightButtonClick = false

    /**
     * Position de la souris au dernier frame
     */
    private var latestMousePos = Point()

    private var latestCameraPos = camera.position.cpy()

    /**
     * La position du début et de la fin du box
     */
    private val selectRectangleData = SelectRectangleData(Point(), Point())

    private var backupTryModeCameraPos = Vector3()
    private var backupTryModeCameraZoom = 1f

    private val editorSceneUI = EditorSceneUI(level.background)

    private var targetCameraZoom = 1f

    init {
        entityContainer.allowUpdatingGO = false
        level.updateCamera(camera, false)

        // Permet de décaler le viewport vers le bas pour afficher la totalité du niveau avec la barre de menu.
        viewport.screenHeight -= editorSceneUI.menuBarHeight
        viewport.apply()

        if (applyMusicTransition)
            MusicManager.startMusic(Constants.menuMusicPath, true)

        PCInputProcessor.scrolledSignal.register {
            if (isUIHover)
                return@register
            targetCameraZoom = (targetCameraZoom + (it * targetCameraZoom * 0.05f)).clamp(0.5f, level.matrixRect.width / camera.viewportWidth).clamp(0.5f, level.matrixRect.height / camera.viewportHeight)
        }
    }

    override fun postBatchRender() {
        super.postBatchRender()

        drawUI()

        entityContainer.cast<Level>()?.drawDebug()

        shapeRenderer.projectionMatrix = camera.combined

        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)

        shapeRenderer.withColor(Color.GOLDENROD) {
            rect(level.matrixRect)
        }

        if (gridMode.active && editorSceneUI.editorMode != EditorSceneUI.EditorMode.TRY_LEVEL) {
            shapeRenderer.withColor(Color.DARK_GRAY) {
                gridMode.walkCells(level.activeRect) {
                    rect(it)
                }
            }
        }

        /**
         * Dessine les entités qui n'ont pas d'AtlasComponent avec un rectangle noir
         */
        entityContainer.cast<Level>()?.apply {
            getAllEntitiesInCells(getActiveGridCells()).forEach {
                if (it.getCurrentState().getComponent<AtlasComponent>()?.data?.isEmpty() != false) {
                    shapeRenderer.withColor(Color.GRAY) {
                        rect(it.box)
                    }
                }
            }
        }

        when (editorSceneUI.editorMode) {
            EditorSceneUI.EditorMode.NO_MODE -> {
                /**
                 * Dessine le rectangle en cour de création
                 */
                if (selectRectangleData.rectangleStarted) {
                    val rect = selectRectangleData.getRect()
                    shapeRenderer.set(ShapeRenderer.ShapeType.Filled)

                    shapeRenderer.withColor(Color(34/255f, 42/255f, 53/255f, 0.3f)) {
                        rect(rect)
                    }
                    shapeRenderer.set(ShapeRenderer.ShapeType.Line)
                    shapeRenderer.withColor(Color(40/255f, 44/255f, 52/255f, 1f)) {
                        rect(rect)
                    }
                }
            }
            EditorSceneUI.EditorMode.SELECT -> {
                /**
                 * Dessine un rectangle autour des entités sélectionnées
                 */
                selectEntities.forEach {
                    shapeRenderer.withColor(if (it === selectEntity) Color(40/255f, 44/255f, 52/255f, 1f) else Color(34/255f, 42/255f, 53/255f, 0.3f)) {
                        rect(it.box)
                    }
                }

                if (selectEntity != null && selectEntityMode == SelectGOMode.NO_MODE || selectEntityMode == SelectGOMode.DIAGONALE_RESIZE) {
                    val rect = selectEntity!!.box

                    shapeRenderer.withColor(Color.RED) {
                        circle(rect.right(), rect.top(), if (selectEntityMode == SelectGOMode.DIAGONALE_RESIZE) 12f else 10f)
                    }
                }
            }
            EditorSceneUI.EditorMode.COPY -> {
                val mousePosInWorld: Point = viewport.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)).let { Point(it.x, it.y) }

                selectEntities.forEach { go ->
                    val rect: Rect? = if (gridMode.active && selectEntities.size == 1) {
                        val pos = if (go === selectEntity) Point(mousePosInWorld.x, mousePosInWorld.y) else Point(mousePosInWorld.x + (go.position().x - (selectEntity?.position()?.x
                                ?: 0f)), mousePosInWorld.y + (go.position().y - (selectEntity?.position()?.y
                                ?: 0f)))
                        gridMode.getRectCellOf(level.activeRect, pos)
                    } else {
                        val pos = if (go === selectEntity) Point(mousePosInWorld.x - go.size().width / 2, mousePosInWorld.y - go.size().height / 2) else Point(mousePosInWorld.x + (go.position().x - (selectEntity?.let { it.position().x + it.size().width / 2 }
                                ?: 0f)), mousePosInWorld.y + (go.position().y - (selectEntity?.let { it.position().y + it.size().height / 2 }
                                ?: 0f)))
                        Rect(pos, go.box.size)
                    }

                    if (rect != null) {
                        go.getCurrentState().getComponent<AtlasComponent>()?.apply {
                            if (this.currentIndex in data.indices) {
                                val data = this.data[currentIndex]
                                PCGame.mainBatch.use {
                                    it.withColor(Color.WHITE.apply { a = 0.5f }) {
                                        it.draw(data.currentKeyFrame(), rect)
                                    }
                                }
                            }
                        } ?: shapeRenderer.withColor(Color.GRAY) { rect(rect) }
                    }

                    if (go.container != null) {
                        shapeRenderer.withColor(if (go === selectEntity) Color.GREEN else Color.OLIVE) {
                            rect(go.box)
                        }
                    }
                }
            }
            EditorSceneUI.EditorMode.TRY_LEVEL -> {
            }
            EditorSceneUI.EditorMode.SELECT_POINT -> {
                PCGame.mainBatch.projectionMatrix = PCGame.defaultProjection
                PCGame.mainBatch.use {
                    val layout = GlyphLayout(editorFont, "Sélectionner un point")
                    editorFont.draw(it, layout, Gdx.graphics.width / 2f - layout.width / 2, Gdx.graphics.height - editorFont.lineHeight * 3)
                }
            }
            EditorSceneUI.EditorMode.SELECT_GO -> {
                PCGame.mainBatch.projectionMatrix = PCGame.defaultProjection
                PCGame.mainBatch.use {
                    val layout = GlyphLayout(editorFont, "Sélectionner une entité")
                    editorFont.draw(it, layout, Gdx.graphics.width / 2f - layout.width / 2, Gdx.graphics.height - editorFont.lineHeight * 3)
                }
            }
        }
        shapeRenderer.end()
    }

    override fun update() {
        super.update()

        updateCamera()

        // TODO Refactor
        if (!isUIHover && editorSceneUI.editorMode == EditorSceneUI.EditorMode.TRY_LEVEL) {
            if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && !latestLeftButtonClick) {
                val entity = findEntityUnderMouse(false)
                if (entity != null) {
                    selectEntityTryMode = entity
                    editorSceneUI.showInfoEntityTextWindow = true
                }
            }
        }

        if (!isUIHover && editorSceneUI.editorMode != EditorSceneUI.EditorMode.TRY_LEVEL) {
            level.activeRect.set(Size((Constants.viewportRatioWidth * camera.zoom).roundToInt(), (camera.viewportHeight * camera.zoom).roundToInt()), Point(camera.position.x - ((Constants.viewportRatioWidth * camera.zoom) / 2f).roundToInt(), camera.position.y - ((Constants.viewportRatioHeight * camera.zoom) / 2f).roundToInt()))

            if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_UP_LAYER.key)) {
                selectLayer += 1
            }
            if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_DOWN_LAYER.key)) {
                selectLayer -= 1
            }

            if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_SWITCH_RESIZE_MODE.key)) {
                resizeEntityMode = if (resizeEntityMode == ResizeMode.PROPORTIONAL) ResizeMode.FREE else ResizeMode.PROPORTIONAL
            }

            val mousePosVec2 = Vector2(Gdx.input.x.toFloat(), Gdx.input.y.toFloat())
            val mousePos = mousePosVec2.toPoint()
            val mousePosInWorld = viewport.unproject(Vector3(mousePosVec2, 0f)).toPoint()
            val latestMousePosInWorld = viewport.unproject(Vector3(latestMousePos.x, latestMousePos.y, 0f)).toPoint()

            when (editorSceneUI.editorMode) {
                EditorSceneUI.EditorMode.NO_MODE -> {
                    if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
                        val roundMousePosInWorld = Point(mousePosInWorld.x.roundToInt().toFloat(), mousePosInWorld.y.roundToInt().toFloat())
                        if (latestLeftButtonClick) { // Rectangle
                            selectRectangleData.endPosition = roundMousePosInWorld
                        } else { // Select
                            val entity = findEntityUnderMouse(true)
                            if (entity != null) {
                                addSelectEntity(entity)
                                editorSceneUI.editorMode = EditorSceneUI.EditorMode.SELECT
                            } else { // Maybe box
                                selectRectangleData.rectangleStarted = true
                                selectRectangleData.startPosition = roundMousePosInWorld
                                selectRectangleData.endPosition = selectRectangleData.startPosition
                            }
                        }
                    } else if (latestLeftButtonClick && selectRectangleData.rectangleStarted) { // Bouton gauche de la souris relaché pendant cette frame
                        selectRectangleData.rectangleStarted = false

                        level.getAllEntitiesInCells(selectRectangleData.getRect()).forEach {
                            if (selectRectangleData.getRect().contains(it.box, true)) {
                                addSelectEntity(it)
                                editorSceneUI.editorMode = EditorSceneUI.EditorMode.SELECT
                            }
                        }
                    }

                    if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_COPY_MODE.key)) {
                        val entity = findEntityUnderMouse(true)
                        if (entity != null) {
                            addSelectEntity(entity)
                            editorSceneUI.editorMode = EditorSceneUI.EditorMode.COPY
                        }
                    }

                    if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_FLIPX.key)) {
                        findEntityUnderMouse(true)?.getCurrentState()?.getComponent<AtlasComponent>()?.apply {
                            flipX = !flipX
                        }
                    }
                    if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_FLIPY.key)) {
                        findEntityUnderMouse(true)?.getCurrentState()?.getComponent<AtlasComponent>()?.apply {
                            flipY = !flipY
                        }
                    }
                    if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_ATLAS_PREVIOUS_FRAME.key)) {
                        findEntityUnderMouse(true)?.apply {
                            if (getStates().size == 1) {
                                getCurrentState().getComponent<AtlasComponent>()?.apply {
                                    if (data.size == 1)
                                        data.elementAt(0).previousFrameRegion(0)
                                }
                            }
                        }
                    }
                    if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_ATLAS_NEXT_FRAME.key)) {
                        findEntityUnderMouse(true)?.apply {
                            if (getStates().size == 1) {
                                getCurrentState().getComponent<AtlasComponent>()?.apply {
                                    if (data.size == 1)
                                        data.elementAt(0).nextFrameRegion(0)
                                }
                            }
                        }
                    }
                }
                EditorSceneUI.EditorMode.SELECT -> {
                    if (selectEntities.isEmpty() || selectEntity == null) {
                        editorSceneUI.editorMode = EditorSceneUI.EditorMode.NO_MODE
                        return
                    }

                    /**
                     * Permet de déplacer les entités sélectionnées
                     */
                    fun moveEntities(moveX: Int, moveY: Int) {
                        val (canMoveX, canMoveY) = let {
                            var canMoveX = true
                            var canMoveY = true

                            selectEntities.forEach {
                                if ((it.box.x + moveX) !in 0..level.matrixRect.width - it.box.width)
                                    canMoveX = false
                                if ((it.box.y + moveY) !in 0..level.matrixRect.height - it.box.height)
                                    canMoveY = false
                            }

                            canMoveX to canMoveY
                        }

                        selectEntities.forEach {
                            it.box.move(if (canMoveX) moveX.toFloat() else 0f, if (canMoveY) moveY.toFloat() else 0f)
                        }
                    }

                    val selectGORect = selectEntity!!.box

                    if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && !Gdx.input.isKeyPressed(GameKeys.EDITOR_APPEND_SELECT_ENTITIES.key)) {
                        if (!latestLeftButtonClick) {
                            if (selectEntityMode == SelectGOMode.NO_MODE) {
                                val entity = findEntityUnderMouse(true)
                                when {
                                    entity == null -> { // Se produit lorsque le joueur clique dans le vide, dans ce cas on désélectionne les entités sélectionnées
                                        clearSelectEntities()
                                        editorSceneUI.editorMode = EditorSceneUI.EditorMode.NO_MODE
                                    }
                                    selectEntities.contains(entity) -> // Dans ce cas-ci, le joueur à sélectionné une autre entité dans celles qui sont sélectionnées
                                        selectEntity = entity
                                    else -> { // Dans ce cas-ci, il y a un groupe d'entité sélectionné ou aucun et le joueur en sélectionne un nouveau ou un en dehors de la sélection
                                        clearSelectEntities()
                                        addSelectEntity(entity)
                                    }
                                }
                            }

                        } else if (selectEntity != null) { // Le joueur maintient le clique gauche durant plusieurs frames et a bougé la souris {
                            if (selectEntityMode == SelectGOMode.NO_MODE)
                                selectEntityMode = SelectGOMode.MOVE

                            val deltaMouseX = (latestMousePosInWorld.x - mousePosInWorld.x).roundToInt()
                            val deltaMouseY = (latestMousePosInWorld.y - mousePosInWorld.y).roundToInt()

                            when (selectEntityMode) {
                                SelectGOMode.NO_MODE -> {
                                }
                                SelectGOMode.HORIZONTAL_RESIZE_RIGHT -> {
                                    selectGORect.width = (selectGORect.width - deltaMouseX).clamp(1, Constants.maxEntitySize)
                                }
                                SelectGOMode.HORIZONTAL_RESIZE_LEFT -> {
                                    selectGORect.width = (selectGORect.width + deltaMouseX).clamp(1, Constants.maxEntitySize)
                                    selectGORect.x = (selectGORect.x - deltaMouseX).clamp(0f, level.matrixRect.width.toFloat() - selectGORect.width)
                                }
                                SelectGOMode.VERTICAL_RESIZE_BOTTOM -> {
                                    selectGORect.height = (selectGORect.height + deltaMouseY).clamp(1, Constants.maxEntitySize)
                                    selectGORect.y = (selectGORect.y - deltaMouseY).clamp(0f, level.matrixRect.height.toFloat() - selectGORect.height)
                                }
                                SelectGOMode.VERTICAL_RESIZE_TOP -> {
                                    selectGORect.height = (selectGORect.height - deltaMouseY).clamp(1, Constants.maxEntitySize)
                                }
                                SelectGOMode.DIAGONALE_RESIZE -> {
                                    var deltaX = deltaMouseX
                                    var deltaY = deltaMouseY

                                    if (resizeEntityMode == ResizeMode.PROPORTIONAL) {
                                        if (Math.abs(deltaX) > Math.abs(deltaY))
                                            deltaY = deltaX
                                        else
                                            deltaX = deltaY
                                    }

                                    selectGORect.width = (selectGORect.width - deltaX).clamp(1, Constants.maxEntitySize)
                                    selectGORect.height = (selectGORect.height - deltaY).clamp(1, Constants.maxEntitySize)
                                }
                                SelectGOMode.MOVE -> {
                                    val moveX = -deltaMouseX + (camera.position.x - latestCameraPos.x)
                                    val moveY = -deltaMouseY + (camera.position.y - latestCameraPos.y)

                                    moveEntities(moveX.roundToInt(), moveY.roundToInt())
                                }
                            }
                        }
                        // Le bouton gauche n'est pas appuyé pendant cette frame
                    } else {
                        when {
                        // Diagonale resize
                            Circle(selectGORect.right(), selectGORect.top(), 10f).contains(mousePosInWorld) -> {
                                selectEntityMode = SelectGOMode.DIAGONALE_RESIZE
                            }
                        // Horizontal right resize
                            mousePosInWorld.x in selectGORect.right() - 1..selectGORect.right() + 1 && mousePosInWorld.y in selectGORect.y..selectGORect.top() -> {
                                selectEntityMode = SelectGOMode.HORIZONTAL_RESIZE_RIGHT
                                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.HorizontalResize)
                            }
                        // Horizontal left resize
                            mousePosInWorld.x in selectGORect.left() - 1..selectGORect.left() + 1 && mousePosInWorld.y in selectGORect.y..selectGORect.top() -> {
                                selectEntityMode = SelectGOMode.HORIZONTAL_RESIZE_LEFT
                                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.HorizontalResize)
                            }
                        // Vertical top resize
                            mousePosInWorld.y in selectGORect.top() - 1..selectGORect.top() + 1 && mousePosInWorld.x in selectGORect.x..selectGORect.right() -> {
                                selectEntityMode = SelectGOMode.VERTICAL_RESIZE_TOP
                                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.VerticalResize)
                            }
                        // Vertical bottom resize
                            mousePosInWorld.y in selectGORect.bottom() - 1..selectGORect.bottom() + 1 && mousePosInWorld.x in selectGORect.x..selectGORect.right() -> {
                                selectEntityMode = SelectGOMode.VERTICAL_RESIZE_BOTTOM
                                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.VerticalResize)
                            }
                            else -> {
                                selectEntityMode = SelectGOMode.NO_MODE
                                Gdx.graphics.setSystemCursor(Cursor.SystemCursor.Arrow)
                            }
                        }

                        if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT) && !latestRightButtonClick) {
                            if (findEntityUnderMouse(false) == null) {
                                clearSelectEntities()
                                editorSceneUI.editorMode = EditorSceneUI.EditorMode.NO_MODE
                            } else {
                                editorSceneUI.showInfoEntityWindow = true
                            }
                        }
                        if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_MOVE_ENTITY_LEFT.key)) {
                            moveEntities(-1, 0)
                        }
                        if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_MOVE_ENTITY_RIGHT.key)) {
                            moveEntities(1, 0)
                        }
                        if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_MOVE_ENTITY_UP.key)) {
                            moveEntities(0, 1)
                        }
                        if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_MOVE_ENTITY_DOWN.key)) {
                            moveEntities(0, -1)
                        }

                        if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_COPY_MODE.key))
                            editorSceneUI.editorMode = EditorSceneUI.EditorMode.COPY
                    }
                }
                EditorSceneUI.EditorMode.COPY -> {
                    if (selectEntities.isEmpty() && selectEntity == null /* Permet de vérifier si on est pas entrain d'ajouter une nouvelle entité */) {
                        editorSceneUI.editorMode = EditorSceneUI.EditorMode.NO_MODE
                        return
                    }

                    if ((Gdx.input.isButtonPressed(Input.Buttons.LEFT) || Gdx.input.isKeyJustPressed(GameKeys.EDITOR_COPY_MODE.key))
                            && !Gdx.input.isKeyPressed(GameKeys.EDITOR_APPEND_SELECT_ENTITIES.key)) {
                        val underMouseGO = findEntityUnderMouse(true)
                        if (underMouseGO != null) {
                            if (selectEntities.contains(underMouseGO))
                                selectEntity = underMouseGO
                            else {
                                clearSelectEntities()
                                addSelectEntity(underMouseGO)
                            }
                        } else {
                            clearSelectEntities()
                            editorSceneUI.editorMode = EditorSceneUI.EditorMode.NO_MODE
                        }
                    }

                    if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT) && (!latestRightButtonClick || gridMode.active)) {
                        val copySelectGO = SerializationFactory.copy(selectEntity!!)

                        var posX = copySelectGO.position().x
                        var posY = copySelectGO.position().y

                        val width = copySelectGO.size().width
                        val height = copySelectGO.size().height

                        var moveToCopyGO = true

                        var useMousePos = false
                        if (selectEntity!!.container != null) {
                            when {
                                Gdx.input.isKeyPressed(GameKeys.EDITOR_SMARTCOPY_LEFT.key) -> {
                                    val minXPos = let {
                                        var x = selectEntity!!.position().x
                                        selectEntities.forEach {
                                            x = Math.min(x, it.position().x)
                                        }
                                        x
                                    }
                                    posX = minXPos - width
                                }
                                Gdx.input.isKeyPressed(GameKeys.EDITOR_SMARTCOPY_RIGHT.key) -> {
                                    val maxXPos = let {
                                        var x = selectEntity!!.position().x
                                        selectEntities.forEach {
                                            x = Math.max(x, it.position().x)
                                        }
                                        x
                                    }
                                    posX = maxXPos + width
                                }
                                Gdx.input.isKeyPressed(GameKeys.EDITOR_SMARTCOPY_DOWN.key) -> {
                                    val minYPos = let {
                                        var y = selectEntity!!.position().y
                                        selectEntities.forEach {
                                            y = Math.min(y, it.position().y)
                                        }
                                        y
                                    }
                                    posY = minYPos - height
                                }
                                Gdx.input.isKeyPressed(GameKeys.EDITOR_SMARTCOPY_UP.key) -> {
                                    val maxYPos = let {
                                        var y = selectEntity!!.position().y
                                        selectEntities.forEach {
                                            y = Math.max(y, it.position().y)
                                        }
                                        y
                                    }
                                    posY = maxYPos + height
                                }
                                else -> useMousePos = true
                            }
                        } else
                            useMousePos = true

                        if (useMousePos) {
                            posX = mousePosInWorld.x - width / 2
                            posY = mousePosInWorld.y - height / 2

                            // Permet de vérifier si l'entité copiée est nouvelle ou pas (si elle est nouvelle, ça veut dire qu'elle n'a pas encore de conteneur)
                            if (selectEntity!!.container != null)
                                moveToCopyGO = false
                        }

                        posX = posX.clamp(0f, level.matrixRect.width.toFloat() - width).roundToInt().toFloat()
                        posY = posY.clamp(0f, level.matrixRect.height.toFloat() - height).roundToInt().toFloat()

                        var putSuccessful = true
                        if (gridMode.active && useMousePos && selectEntities.size == 1) {
                            putSuccessful = gridMode.putEntity(level.activeRect, Point(mousePosInWorld.x, mousePosInWorld.y), copySelectGO, level)
                        } else
                            copySelectGO.box.position = Point(posX, posY)

                        val copyEntities = mutableListOf<Entity>()

                        if (putSuccessful) {
                            level.addEntity(copySelectGO)
                            copyEntities.add(copySelectGO)
                        } else
                            moveToCopyGO = false

                        selectEntities.filter { it !== selectEntity }.forEach {
                            val deltaX = it.position().x - selectEntity!!.position().x
                            val deltaY = it.position().y - selectEntity!!.position().y

                            level.addEntity(SerializationFactory.copy(it).apply {
                                val pos = Point((copySelectGO.position().x + deltaX).clamp(0f, level.matrixRect.width.toFloat() - this.size().width), (copySelectGO.position().y + deltaY).clamp(0f, level.matrixRect.height.toFloat() - this.size().height))

                                this.box.position = pos

                                copyEntities += this
                            })
                        }

                        if (moveToCopyGO) {
                            clearSelectEntities()
                            copyEntities.forEach { addSelectEntity(it) }
                        }
                    }
                }
                EditorSceneUI.EditorMode.SELECT_POINT -> {
                    if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && !latestLeftButtonClick) {
                        editorSceneUI.onSelectPoint(mousePosInWorld)
                        editorSceneUI.editorMode = EditorSceneUI.EditorMode.NO_MODE
                    }
                }
                EditorSceneUI.EditorMode.SELECT_GO -> {
                    if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && !latestLeftButtonClick) {
                        val go = findEntityUnderMouse(false)
                        editorSceneUI.onSelectGO(go)
                        editorSceneUI.editorMode = EditorSceneUI.EditorMode.NO_MODE
                    }
                }
            }

            if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_REMOVE_ENTITY.key)) {
                val entity = findEntityUnderMouse(false)
                if (entity != null) {
                    removeEntity(entity)
                    if (selectEntities.contains(entity))
                        selectEntities.remove(entity)
                } else if (selectEntities.isNotEmpty()) {
                    selectEntities.forEach { removeEntity(it) }
                    clearSelectEntities()
                    editorSceneUI.editorMode = EditorSceneUI.EditorMode.NO_MODE
                }
            }

            if (Gdx.input.isButtonPressed(Input.Buttons.LEFT) && !latestLeftButtonClick && Gdx.input.isKeyPressed(GameKeys.EDITOR_APPEND_SELECT_ENTITIES.key)) {
                if (selectEntities.isNotEmpty()) {
                    val underMouseGO = findEntityUnderMouse(true)
                    if (underMouseGO != null) {
                        if (selectEntities.contains(underMouseGO)) {
                            selectEntities.remove(underMouseGO)
                            if (selectEntity === underMouseGO)
                                selectEntity = null
                        } else
                            addSelectEntity(underMouseGO)
                    }
                }
            }

            latestLeftButtonClick = Gdx.input.isButtonPressed(Input.Buttons.LEFT)
            latestRightButtonClick = Gdx.input.isButtonPressed(Input.Buttons.RIGHT)
            latestMousePos = mousePos
            latestCameraPos = camera.position.cpy()
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (editorSceneUI.editorMode == EditorSceneUI.EditorMode.TRY_LEVEL)
                finishTryLevel()
            else
                editorSceneUI.showExitWindow = true
        }

        if (!isUIHover && Gdx.input.isKeyJustPressed(GameKeys.EDITOR_GRID_MODE.key) && editorSceneUI.editorMode != EditorSceneUI.EditorMode.TRY_LEVEL) {
            gridMode.active = !gridMode.active
        }

        if (Gdx.input.isKeyJustPressed(GameKeys.EDITOR_TRY_LEVEL.key)) {
            if (editorSceneUI.editorMode != EditorSceneUI.EditorMode.TRY_LEVEL)
                launchTryLevel()
            else
                finishTryLevel()
        }
    }

    override fun dispose() {
        super.dispose()
        editorFont.dispose()

        if (!level.levelPath.toLocalFile().exists()) {
            level.deleteFiles()
        }
    }

    private fun updateCamera() {
        var moveCameraX = 0f
        var moveCameraY = 0f

        if (targetCameraZoom != camera.zoom) {
            val px = camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f))

            camera.zoom = MathUtils.lerp(camera.zoom, targetCameraZoom, 0.1f)

            camera.update()

            val nextPX = camera.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f))

            camera.position.add(px.x - nextPX.x, px.y - nextPX.y, 0f)
        }

        if (camera.zoom > level.matrixRect.width / camera.viewportWidth || camera.zoom > level.matrixRect.height / camera.viewportHeight) {
            targetCameraZoom = camera.zoom.clamp(0.5f, level.matrixRect.width / camera.viewportWidth).clamp(0.5f, level.matrixRect.height / camera.viewportHeight)
        }

        if (editorSceneUI.editorMode == EditorSceneUI.EditorMode.TRY_LEVEL) {
            entityContainer.cast<Level>()?.updateCamera(camera, true)
        } else if (!isUIHover) {
            if (Gdx.input.isKeyPressed(GameKeys.EDITOR_CAMERA_LEFT.key))
                moveCameraX -= cameraMoveSpeed
            if (Gdx.input.isKeyPressed(GameKeys.EDITOR_CAMERA_RIGHT.key))
                moveCameraX += cameraMoveSpeed
            if (Gdx.input.isKeyPressed(GameKeys.EDITOR_CAMERA_UP.key))
                moveCameraY += cameraMoveSpeed
            if (Gdx.input.isKeyPressed(GameKeys.EDITOR_CAMERA_DOWN.key))
                moveCameraY -= cameraMoveSpeed

            if (Gdx.input.isKeyPressed(GameKeys.CAMERA_ZOOM_RESET.key))
                targetCameraZoom = 1f

            val minCameraX = camera.zoom * (camera.viewportWidth / 2)
            val maxCameraX = level.matrixRect.width - minCameraX
            val minCameraY = camera.zoom * (camera.viewportHeight / 2)
            val maxCameraY = level.matrixRect.height - minCameraY

            val x = MathUtils.lerp(camera.position.x, camera.position.x + moveCameraX, 0.5f)
            val y = MathUtils.lerp(camera.position.y, camera.position.y + moveCameraY, 0.5f)

            camera.position.set(MathUtils.clamp(x, minCameraX, maxCameraX), MathUtils.clamp(y, minCameraY, maxCameraY), 0f)
        }

        camera.update()
    }

    private fun setCopyEntity(entity: Entity) {
        clearSelectEntities()
        addSelectEntity(entity)
        editorSceneUI.editorMode = EditorSceneUI.EditorMode.COPY
    }

    /**
     * Permet d'ajouter une nouvelle entité sélectionnée
     */
    private fun addSelectEntity(entity: Entity) {
        selectEntities.add(entity)

        if (selectEntities.size == 1) {
            selectEntity = entity
        }
    }

    /**
     * Permet de supprimer les entités sélectionnées de la liste -> ils ne sont pas supprimés du niveau, juste déséléctionnés
     */
    private fun clearSelectEntities() {
        selectEntities.clear()
        selectEntity = null
    }

    /**
     * Permet de retourner l'entité sous le pointeur par rapport à son layer
     */
    private fun findEntityUnderMouse(replaceEditorLayer: Boolean): Entity? {
        val mousePosInWorld = viewport.unproject(Vector3(Gdx.input.x.toFloat(), Gdx.input.y.toFloat(), 0f)).toPoint()

        val entitiesUnderMouse = (entityContainer as Level).run { getAllEntitiesInCells(getActiveGridCells()).filter { it.box.contains(mousePosInWorld) } }

        if (!entitiesUnderMouse.isEmpty()) {
            val goodLayerEntity = entitiesUnderMouse.find { it.layer == selectLayer }
            return if (goodLayerEntity != null)
                goodLayerEntity
            else {
                val entity = entitiesUnderMouse.first()
                if (replaceEditorLayer)
                    selectLayer = entity.layer
                entity
            }
        }

        return null
    }

    private fun removeEntity(entity: Entity) {
        if (entity === selectEntity) {
            selectEntity = null
        }
        entity.removeFromParent()
    }

    private fun launchTryLevel() {
        // Permet de garantir que toutes les entités auront bien un alpha de 1 même si la transition vers cette scène n'est pas finie
        alpha = 1f

        backupTryModeCameraPos = camera.position.cpy()
        backupTryModeCameraZoom = camera.zoom

        editorSceneUI.editorMode = EditorSceneUI.EditorMode.TRY_LEVEL

        if (level.musicPath != null)
            MusicManager.startMusic(level.musicPath!!.get(), true)

        entityContainer = SerializationFactory.copy(level).apply {
            this.exit = { if (!editorSceneUI.godModeTryMode) finishTryLevel() }
            this.activeRect.position = level.activeRect.position
            this.drawDebugCells = level.drawDebugCells
            this.update()
        }
    }

    private fun finishTryLevel() {
        clearSelectEntities()
        editorSceneUI.editorMode = EditorSceneUI.EditorMode.NO_MODE

        entityContainer = level
        camera.position.set(backupTryModeCameraPos)
        camera.zoom = backupTryModeCameraZoom

        MusicManager.startMusic(Constants.menuMusicPath, true)

        selectEntityTryMode = null
    }

    private fun saveLevelToFile() {
        try {
            SerializationFactory.serializeToFile(level, level.levelPath.toLocalFile())
        } catch (e: Exception) {
            Log.error(e) { "Erreur lors de l'enregistrement du niveau !" }
        }
    }

    //region UI

    private fun drawUI() {
        with(ImGui) {
            drawMainMenuBar()

            drawInfoEditorWindow()

            if (editorSceneUI.showEntitiesWindow && editorSceneUI.editorMode != EditorSceneUI.EditorMode.TRY_LEVEL)
                drawEntitiesWindow()

            if (gridMode.active && editorSceneUI.editorMode != EditorSceneUI.EditorMode.TRY_LEVEL)
                drawGridSettingsWindow()

            if (editorSceneUI.showInfoPrefabWindow && editorSceneUI.prefabInfoPrefabWindow != null && editorSceneUI.editorMode != EditorSceneUI.EditorMode.TRY_LEVEL)
                drawInfoPrefabWindow(editorSceneUI.prefabInfoPrefabWindow!!)

            val goUnderMouse = findEntityUnderMouse(false)
            if (goUnderMouse != null && !isUIHover) {
                functionalProgramming.withTooltip {
                    ImGuiHelper.textColored(Color.RED, goUnderMouse.name)
                    ImGuiHelper.textPropertyColored(Color.ORANGE, "couche :", goUnderMouse.layer)

                    ImGuiHelper.textPropertyColored(Color.ORANGE, "x :", goUnderMouse.box.x)
                    sameLine()
                    ImGuiHelper.textPropertyColored(Color.ORANGE, "y :", goUnderMouse.box.y)

                    ImGuiHelper.textPropertyColored(Color.ORANGE, "w :", goUnderMouse.box.width)
                    sameLine()
                    ImGuiHelper.textPropertyColored(Color.ORANGE, "h :", goUnderMouse.box.height)
                }
            }

            if (editorSceneUI.showInfoEntityTextWindow && selectEntityTryMode != null && editorSceneUI.editorMode == EditorSceneUI.EditorMode.TRY_LEVEL) {
                drawInfoEntityTextWindow(selectEntityTryMode!!)
            }

            if (editorSceneUI.showInfoEntityWindow && selectEntity != null && selectEntity?.container != null
                    && editorSceneUI.editorMode != EditorSceneUI.EditorMode.SELECT_POINT && editorSceneUI.editorMode != EditorSceneUI.EditorMode.SELECT_GO && editorSceneUI.editorMode != EditorSceneUI.EditorMode.TRY_LEVEL) {
                drawInfoEntityWindow(selectEntity!!)
            }

            if (editorSceneUI.showExitWindow) {
                drawExitWindow()
            }
        }
    }

    private fun drawInfoEditorWindow() {
        with(ImGui) {
            setNextWindowPos(Vec2(10f, 10f + (g.fontBaseSize + g.style.framePadding.y * 2f).roundToInt()), Cond.Once)
            functionalProgramming.withWindow("editor info", null, WindowFlags.AlwaysAutoResize.i or WindowFlags.NoTitleBar.i or WindowFlags.NoBringToFrontOnFocus.i) {
                ImGuiHelper.textPropertyColored(Color.ORANGE, "Nombre d'entités :", entityContainer.getEntitiesData().size)

                if (editorSceneUI.editorMode != EditorSceneUI.EditorMode.TRY_LEVEL) {
                    ImGuiHelper.textPropertyColored(Color.ORANGE, "Couche sélectionnée :", selectLayer)
                    ImGuiHelper.textPropertyColored(Color.ORANGE, "Redimensionnement :", resizeEntityMode.resizeName)
                    ImGuiHelper.textPropertyColored(Color.ORANGE, "Mode de l'éditeur :", editorSceneUI.editorMode.modeName)
                    ImGuiHelper.textPropertyColored(Color.ORANGE, "Niveau de zoom :", Math.round(camera.zoom * 100f) / 100f)
                    functionalProgramming.collapsingHeader("Paramètres de l'éditeur") {
                        checkbox("Afficher la fenêtre Fabrique d'entités", editorSceneUI::showEntitiesWindow)
                        checkbox("Afficher la grille", gridMode::active)
                    }
                } else {
                    ImGuiHelper.textColored(Color.ORANGE, "Test du niveau..")
                    checkbox("Mode dieu", editorSceneUI::godModeTryMode)
                    checkbox("Mettre à jour", entityContainer::allowUpdatingGO)
                }
            }
        }
    }

    private fun drawExitWindow() {
        ImGuiHelper.withCenteredWindow("Save level?", editorSceneUI::showExitWindow, Vec2(240f, 105f), WindowFlags.NoResize.i or WindowFlags.NoCollapse.i or WindowFlags.NoTitleBar.i) {
            fun showMainMenu() {
                PCGame.sceneManager.loadScene(MainMenuScene(false))
            }

            if (ImGui.button("Sauvegarder", Vec2(225f, 0))) {
                saveLevelToFile()
                showMainMenu()
            }
            if (ImGui.button("Abandonner les modifications", Vec2(225f, 0))) {
                if (!level.levelPath.toLocalFile().exists()) {
                    level.deleteFiles()
                }
                showMainMenu()
            }
            if (ImGui.button("Annuler", Vec2(225f, 0))) {
                editorSceneUI.showExitWindow = false
            }
        }
    }

    private fun drawMainMenuBar() {
        with(ImGui) {
            mainMenuBar {
                if (editorSceneUI.editorMode != EditorSceneUI.EditorMode.TRY_LEVEL) {
                    menu("Fichier") {
                        menuItem("Importer une ressource..") {
                            try {
                                stackPush().also { stack ->
                                    val aFilterPatterns = stack.mallocPointer(Constants.levelTextureExtension.size + Constants.levelAtlasExtension.size + Constants.levelSoundExtension.size + Constants.levelScriptExtension.size)

                                    val extensions = Constants.levelTextureExtension + Constants.levelAtlasExtension + Constants.levelSoundExtension + Constants.levelScriptExtension
                                    extensions.forEach {
                                        aFilterPatterns.put(stack.UTF8("*.$it"))
                                    }

                                    aFilterPatterns.flip()

                                    val extensionsStr = let {
                                        var str = String()
                                        extensions.forEach {
                                            str += "*.$it "
                                        }
                                        str
                                    }

                                    val files = tinyfd_openFileDialog("Importer une ressource..", "", aFilterPatterns, "Ressources ($extensionsStr)", true)
                                    if (files != null) {
                                        level.addResources(*files.split("|").map { it.toAbsoluteFile() }.toTypedArray())
                                    }
                                }
                            } catch (e: Exception) {
                                Log.error(e) { "Erreur lors de l'importation d'une ressource !" }
                            }
                        }
                        menuItem("Essayer le niveau") {
                            ImGui.cursorPosY = 100f
                            launchTryLevel()
                        }
                        menuItem("Sauvegarder") {
                            saveLevelToFile()
                        }
                        separator()
                        menuItem("Quitter") {
                            editorSceneUI.showExitWindow = true
                        }
                    }

                    menu("Éditer") {
                        menu("Tags") {
                            val popupTitle = "add tag popup"
                            val it = level.tags.iterator()

                            var counter = 0
                            while (it.hasNext()) {
                                val tag = it.next()

                                functionalProgramming.withId("del btn ${++counter}") {
                                    pushItemFlag(ItemFlags.Disabled.i, Tags.values().any { it.tag == tag })
                                    if (button("Suppr.")) {
                                        it.remove()
                                    }
                                    popItemFlag()
                                }

                                sameLine(0f, style.itemInnerSpacing.x)

                                text(tag)
                            }

                            if (button("Ajouter un tag", Vec2(-1, 0))) {
                                openPopup(popupTitle)
                            }

                            functionalProgramming.popup(popupTitle) {
                                ImGuiHelper.inputText("nom", editorSceneUI::addTagPopupNameBuffer)
                                if (button("Ajouter", Vec2(-1, 0))) {
                                    if (editorSceneUI.addTagPopupNameBuffer.isNotBlank() && level.tags.none { it == editorSceneUI.addTagPopupNameBuffer }) {
                                        level.tags.add(editorSceneUI.addTagPopupNameBuffer)
                                        closeCurrentPopup()
                                    }
                                }
                            }
                        }
                        menu("Options du niveau") {
                            fun updateBackground(newBackground: Background) {
                                editorSceneUI.settingsLevelBackgroundType.obj = newBackground.type
                                level.background = newBackground
                                background = newBackground
                            }

                            functionalProgramming.collapsingHeader("arrière plan") {
                                functionalProgramming.withIndent {
                                    val colorPopupTitle = "level background color popup"

                                    text("couleur : ")
                                    sameLine(0f, style.itemInnerSpacing.x)
                                    if (colorButton("level background color", Vec4(level.backgroundColor[0], level.backgroundColor[1], level.backgroundColor[2], 0f))) {
                                        openPopup(colorPopupTitle)
                                    }

                                    ImGuiHelper.enum("type de fond d'écran", editorSceneUI.settingsLevelBackgroundType)

                                    when (editorSceneUI.settingsLevelBackgroundType.obj.cast<BackgroundType>()) {
                                        BackgroundType.Standard -> {
                                            if (editorSceneUI.settingsLevelStandardBackgroundIndex[0] == -1) {
                                                editorSceneUI.settingsLevelStandardBackgroundIndex[0] = PCGame.standardBackgrounds().indexOfFirst { it.backgroundFile == (level.background.cast<StandardBackground>())?.backgroundFile }
                                            }

                                            functionalProgramming.withItemWidth(Constants.defaultWidgetsWidth) {
                                                if (sliderInt("fond d'écran", editorSceneUI.settingsLevelStandardBackgroundIndex, 0, PCGame.standardBackgrounds().size - 1)) {
                                                    updateBackground(PCGame.standardBackgrounds()[editorSceneUI.settingsLevelStandardBackgroundIndex[0]])
                                                }
                                            }
                                        }
                                        BackgroundType.Parallax -> {
                                            if (editorSceneUI.settingsLevelParallaxBackgroundIndex[0] == -1) {
                                                editorSceneUI.settingsLevelParallaxBackgroundIndex[0] = PCGame.parallaxBackgrounds().indexOfFirst { it.parallaxDataFile == (level.background.cast<ParallaxBackground>())?.parallaxDataFile }
                                            }

                                            functionalProgramming.withItemWidth(Constants.defaultWidgetsWidth) {
                                                if (sliderInt("fond d'écran", editorSceneUI.settingsLevelParallaxBackgroundIndex, 0, PCGame.parallaxBackgrounds().size - 1)) {
                                                    updateBackground(PCGame.parallaxBackgrounds()[editorSceneUI.settingsLevelParallaxBackgroundIndex[0]])
                                                }
                                            }
                                        }
                                        BackgroundType.None -> {
                                            level.background = null
                                            background = null
                                        }
                                    }

                                    functionalProgramming.popup(colorPopupTitle) {
                                        if (colorEdit3("couleur de l'arrière plan", level.backgroundColor))
                                            backgroundColors = level.backgroundColor
                                    }
                                }
                            }

                            val currentMusicIndex = let {
                                if (level.musicPath != null)
                                    intArrayOf(PCGame.gameMusics.indexOf(level.musicPath!!.get()))
                                else
                                    intArrayOf(-1)
                            }
                            functionalProgramming.withItemWidth(Constants.defaultWidgetsWidth) {
                                if (combo("musique", currentMusicIndex, PCGame.gameMusics.map { it.nameWithoutExtension() })) {
                                    level.musicPath = PCGame.gameMusics[currentMusicIndex[0]].toFileWrapper()
                                }
                            }

                            ImGuiHelper.entity(level::followEntity, level, editorSceneUI, "entité suivie")

                            functionalProgramming.withItemWidth(Constants.defaultWidgetsWidth) {
                                inputInt("gravité", level::gravitySpeed)
                                inputInt("largeur", level::matrixWidth)
                                inputInt("hauteur", level::matrixHeight)
                                sliderFloat("zoom initial", level::initialZoom, 0.1f, 2f, "%.1f")
                            }
                        }
                    }
                    menu("Créer une entité") {
                        fun createGO(prefab: Prefab) {
                            val entity = prefab.create(Point()).apply { loadResources() }
                            setCopyEntity(entity)
                        }

                        PrefabType.values().forEach { type ->
                            menu(type.name) {
                                if (type == PrefabType.All) {
                                    level.resourcesPrefabs().forEach {
                                        menuItem(it.name) {
                                            createGO(it)
                                        }
                                    }
                                }
                                PrefabFactory.values().filter { it.type == type }.forEach {
                                    menuItem(it.name.removeSuffix("_${type.name}")) {
                                        createGO(it.prefab)
                                    }
                                }
                            }
                        }
                    }
                    ImGui.cursorPosX = Gdx.graphics.width.toFloat() - 175f

                    val btns = {
                        if (smallButton("Sauvegarder"))
                            saveLevelToFile()

                        sameLine(0f, style.itemInnerSpacing.x)
                        functionalProgramming.withStyleColor(Col.Text, Vec4.fromColor(102, 255, 147, 255)) {
                            if (smallButton("Essayer"))
                                launchTryLevel()
                        }
                    }

                    if(PCGame.darkUI) {
                        functionalProgramming.withStyleColor(Col.Button, Vec4.fromColor(49, 54, 62, 200), Col.ButtonHovered, Vec4.fromColor(49, 54, 62, 255), Col.ButtonActive, Vec4.fromColor(49, 54, 62, 255)) {
                            btns()
                        }
                    }
                    else
                        btns()

                } else {
                    menuItem("Arrêter l'essai") {
                        finishTryLevel()
                    }
                }
            }
        }
    }

    private fun drawGridSettingsWindow() {
        with(ImGui) {
            functionalProgramming.withWindow("Réglages de la grille", null, WindowFlags.AlwaysAutoResize.i) {
                val size = intArrayOf(gridMode.cellWidth, gridMode.cellHeight)

                functionalProgramming.withItemWidth(Constants.defaultWidgetsWidth) {
                    if (inputInt2("taille", size)) {
                        gridMode.cellWidth = size[0].clamp(1, Constants.maxEntitySize)
                        gridMode.cellHeight = size[1].clamp(1, Constants.maxEntitySize)
                        gridMode.offsetX = gridMode.offsetX.min(gridMode.cellWidth)
                        gridMode.offsetY = gridMode.offsetY.min(gridMode.cellHeight)
                    }
                }

                functionalProgramming.withItemWidth(Constants.defaultWidgetsWidth) {
                    dragInt("décalage x", gridMode::offsetX, 1f, 0, gridMode.cellWidth)
                    dragInt("décalage y", gridMode::offsetY, 1f, 0, gridMode.cellHeight)
                }
            }
        }
    }

    private fun drawEntitiesWindow() {
        with(ImGui) {
            val nextWindowSize = Vec2(210f, Gdx.graphics.height - editorSceneUI.menuBarHeight)
            setNextWindowSize(nextWindowSize, Cond.Once)
            setNextWindowPos(Vec2(Gdx.graphics.width - nextWindowSize.x, editorSceneUI.menuBarHeight), Cond.Once)
            setNextWindowSizeConstraints(Vec2(nextWindowSize.x, 250f), Vec2(nextWindowSize.x, Gdx.graphics.height))
            functionalProgramming.withWindow("Fabrique d'entités", editorSceneUI::showEntitiesWindow) {
                val tags = level.tags.apply { remove(Tags.Empty.tag) }

                ImGuiHelper.comboWithSettingsButton("type", editorSceneUI::entitiesTypeIndex, level.tags.apply { remove(Tags.Empty.tag) }, {
                    val tag = tags.elementAtOrElse(editorSceneUI.entitiesTypeIndex, { Tags.Sprite.tag })
                    checkbox(if (tag == Tags.Sprite.tag) "pack importés" else "afficher seulement les préfabs créés", editorSceneUI::entitiesShowOnlyUser)
                })

                val tag = tags.elementAtOrElse(editorSceneUI.entitiesTypeIndex, { Tags.Sprite.tag })

                fun addImageBtn(region: TextureAtlas.AtlasRegion, prefab: Prefab, showTooltip: Boolean) {
                    if (imageButton(region.texture.textureObjectHandle, Vec2(50f, 50f), Vec2(region.u, region.v), Vec2(region.u2, region.v2))) {
                        setCopyEntity(prefab.create(Point()).apply { loadResources() })
                    }

                    if (showTooltip && isItemHovered()) {
                        functionalProgramming.withTooltip {
                            text(prefab.name)
                        }
                    }
                }

                when (tag) {
                    Tags.Sprite.tag -> {
                        functionalProgramming.withGroup {
                            functionalProgramming.withItemWidth(Constants.defaultWidgetsWidth) {
                                if (!editorSceneUI.entitiesShowOnlyUser) {
                                    combo("dossier", editorSceneUI::entitiesSpritePackTypeIndex, PCGame.gameAtlas.map { it.key.name() })
                                }
                                combo("pack", editorSceneUI::entitiesSpritePackIndex,
                                        if (editorSceneUI.entitiesShowOnlyUser) level.resourcesAtlas().map { it.nameWithoutExtension() }
                                        else PCGame.gameAtlas.entries.elementAtOrNull(editorSceneUI.entitiesSpritePackTypeIndex)?.value?.map { it.nameWithoutExtension() }
                                                ?: arrayListOf())
                                if (!editorSceneUI.entitiesSpriteRealSize)
                                    ImGuiHelper.size(editorSceneUI::entitiesSpriteCustomSize, Size(1), Size(Constants.maxEntitySize))
                                checkbox("Physique", editorSceneUI::entitiesSpritePhysics)
                                checkbox("Taille réelle", editorSceneUI::entitiesSpriteRealSize)
                            }
                        }
                        separator()

                        (if (editorSceneUI.entitiesShowOnlyUser) level.resourcesAtlas().getOrNull(editorSceneUI.entitiesSpritePackIndex) else PCGame.gameAtlas.entries.elementAtOrNull(editorSceneUI.entitiesSpritePackTypeIndex)?.value?.getOrNull(editorSceneUI.entitiesSpritePackIndex))?.also { atlasPath ->
                            val atlas = ResourceManager.getPack(atlasPath)
                            atlas.regions.sortedBy { it.name }.forEachIndexed { index, region ->
                                val atlasRegion = atlasPath.toFileWrapper() to region.name
                                val size = if (editorSceneUI.entitiesSpriteRealSize) region.let { Size(it.regionWidth, it.regionHeight) } else Size(50, 50)
                                val prefab = if (editorSceneUI.entitiesSpritePhysics) PrefabSetup.setupPhysicsSprite(atlasRegion, size) else PrefabSetup.setupSprite(atlasRegion, size)
                                addImageBtn(region, prefab, false)

                                if ((index + 1) % 3 != 0)
                                    sameLine(0f, style.itemInnerSpacing.x)
                            }
                        }
                    }
                    else -> {
                        var index = 0
                        val prefabs = level.resourcesPrefabs() + if (editorSceneUI.entitiesShowOnlyUser) listOf() else PrefabFactory.values().map { it.prefab }
                        prefabs.filter { it.prefabGO.tag == tag }.forEach {
                            it.prefabGO.loadResources()

                            it.prefabGO.getCurrentState().getComponent<AtlasComponent>()?.apply {
                                data.elementAtOrNull(currentIndex)?.apply {
                                    addImageBtn(currentKeyFrame(), it, true)

                                    if (isItemHovered() && Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
                                        editorSceneUI.prefabInfoPrefabWindow = it
                                        editorSceneUI.showInfoPrefabWindow = true
                                    }

                                    if ((++index) % 3 != 0)
                                        sameLine(0f, style.itemInnerSpacing.x)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun drawInfoPrefabWindow(prefab: Prefab) {
        with(ImGui) {
            functionalProgramming.withWindow("Réglages du prefab", editorSceneUI::showInfoPrefabWindow, WindowFlags.AlwaysAutoResize.i) {
                val isGamePrefab = PrefabFactory.values().any { it.prefab === prefab }

                if (isGamePrefab) {
                    ImGuiHelper.textColored(Color.RED, "Modifications temporaires !")
                    if (isItemHovered()) {
                        functionalProgramming.withTooltip {
                            text("Les modifications apportés à ce prefab seront temporaires !\nMerci de créer un nouveau prefab pour sauvegarder les changements apportés.")
                        }
                    }
                } else {
                    if (button("Supprimer ce prefab", Vec2(-1, 0))) {
                        level.removePrefab(prefab)
                        editorSceneUI.showInfoPrefabWindow = false
                        editorSceneUI.prefabInfoPrefabWindow = null
                    }
                }

                drawInfoEntityProps(prefab.prefabGO)
            }
        }
    }

    private fun drawInfoEntityProps(entity: Entity) {
        val newStateTitle = "Nouveau état"
        val addComponentTitle = "Ajouter un component"

        with(ImGui) {
            ImGuiHelper.insertUIFields(entity, entity, level, editorSceneUI)

            separator()

            ImGuiHelper.comboWithSettingsButton("état", editorSceneUI::entityCurrentStateIndex, entity.getStates().map { it.name }, {
                if (button("Ajouter un état")) {
                    openPopup(newStateTitle)
                }

                if (entity.getStates().size > 1) {
                    sameLine()
                    if (button("Supprimer ${entity.getStateOrDefault(editorSceneUI.entityCurrentStateIndex).name}")) {
                        entity.removeState(editorSceneUI.entityCurrentStateIndex)
                        editorSceneUI.entityCurrentStateIndex = Math.max(0, editorSceneUI.entityCurrentStateIndex - 1)
                    }
                }

                functionalProgramming.popup(newStateTitle) {
                    val comboItems = mutableListOf("État vide").apply { addAll(entity.getStates().map { "Copier de : ${it.name}" }) }

                    functionalProgramming.withItemWidth(Constants.defaultWidgetsWidth) {
                        combo("type", editorSceneUI::entityAddStateComboIndex, comboItems)
                    }

                    ImGuiHelper.inputText("nom", editorSceneUI::createStateInputText)

                    if (button("Ajouter", Vec2(Constants.defaultWidgetsWidth, 0))) {
                        if (editorSceneUI.entityAddStateComboIndex == 0)
                            entity.addState(editorSceneUI.createStateInputText) {}
                        else
                            entity.addState(SerializationFactory.copy(entity.getStateOrDefault(editorSceneUI.entityAddStateComboIndex - 1)).apply { name = editorSceneUI.createStateInputText })
                        closeCurrentPopup()
                    }
                }
            })

            ImGuiHelper.action("action de départ", entity.getStateOrDefault(editorSceneUI.entityCurrentStateIndex)::startAction, entity, level, editorSceneUI)

            separator()

            val components = entity.getStateOrDefault(editorSceneUI.entityCurrentStateIndex).getComponents()

            functionalProgramming.withItemWidth(Constants.defaultWidgetsWidth) {
                combo("component", editorSceneUI::entityCurrentComponentIndex, components.map { ReflectionUtility.simpleNameOf(it).removeSuffix("Component") })
            }

            val component = components.elementAtOrNull(editorSceneUI.entityCurrentComponentIndex)
            if (component != null) {
                functionalProgramming.withIndent {
                    val requiredComponent = component.javaClass.kotlin.findAnnotation<RequiredComponent>()
                    val incorrectComponent = let {
                        requiredComponent?.component?.forEach {
                            if (!entity.getStateOrDefault(editorSceneUI.entityCurrentStateIndex).hasComponent(it))
                                return@let true
                        }
                        false
                    }
                    if (!incorrectComponent)
                        ImGuiHelper.insertUIFields(component, entity, level, editorSceneUI)
                    else {
                        text("Il manque le(s) component(s) :")
                        functionalProgramming.withIndent {
                            text("${requiredComponent!!.component.map { it.simpleName }}")
                        }
                    }
                }
                if (button("Supprimer ce comp.", Vec2(-1, 0))) {
                    entity.getStateOrDefault(editorSceneUI.entityCurrentStateIndex).removeComponent(component)
                }
            }
            separator()
            pushItemFlag(ItemFlags.Disabled.i, entity.getStateOrDefault(editorSceneUI.entityCurrentStateIndex).getComponents().size == Components.values().size)
            if (button("Ajouter un component", Vec2(-1, 0)))
                openPopup(addComponentTitle)
            popItemFlag()

            functionalProgramming.popup(addComponentTitle) {
                val components = Components.values().filter { comp -> entity.getStateOrDefault(editorSceneUI.entityCurrentStateIndex).getComponents().none { comp.component.isInstance(it) } }
                functionalProgramming.withItemWidth(Constants.defaultWidgetsWidth) {
                    combo("component", editorSceneUI::entityAddComponentComboIndex, components.map { it.name })
                }

                if (editorSceneUI.entityAddComponentComboIndex in components.indices) {
                    val componentClass = components[editorSceneUI.entityAddComponentComboIndex].component

                    val description = componentClass.findAnnotation<Description>()
                    if (description != null) {
                        sameLine(0f, style.itemInnerSpacing.x)
                        text("(?)")

                        if (isItemHovered()) {
                            functionalProgramming.withTooltip {
                                text(description.description)
                            }
                        }
                    }

                    if (button("Ajouter", Vec2(-1, 0))) {
                        if (editorSceneUI.entityAddComponentComboIndex in components.indices) {
                            val newComp = ReflectionUtility.createInstance(components[editorSceneUI.entityAddComponentComboIndex].component.java)
                            val state = entity.getStateOrDefault(editorSceneUI.entityCurrentStateIndex)

                            if (entity.getCurrentState() === state)
                                newComp.onStateActive(entity, state, level)

                            state.addComponent(newComp)
                            closeCurrentPopup()
                        }
                    }
                }
            }
        }
    }

    private fun drawInfoEntityWindow(entity: Entity) {
        val createPrefabTitle = "Créer un prefab"

        with(ImGui) {
            functionalProgramming.withWindow("Réglages de l'entité", editorSceneUI::showInfoEntityWindow, WindowFlags.AlwaysAutoResize.i) {
                val favChecked = booleanArrayOf(level.favoris.contains(entity))

                if (ImGuiHelper.favButton(tintColor = Vec4(1f, 1f, 1f, if (favChecked[0]) 1f else 0.2f))) {
                    if (!favChecked[0])
                        level.favoris.add(entity)
                    else
                        level.favoris.remove(entity)
                }
                if (isItemHovered()) {
                    functionalProgramming.withTooltip {
                        text("Favoris")
                    }
                }

                sameLine(0f, style.itemInnerSpacing.x)

                if (button("Créer un prefab", Vec2(-1, 0)))
                    openPopup(createPrefabTitle)

                if (button("Supprimer cette entité", Vec2(-1, 0))) {
                    entity.removeFromParent()
                    if (selectEntity === entity) {
                        selectEntity = null
                        clearSelectEntities()
                        editorSceneUI.editorMode = EditorSceneUI.EditorMode.NO_MODE
                    }
                }

                drawInfoEntityProps(entity)

                functionalProgramming.popup(createPrefabTitle) {
                    ImGuiHelper.inputText("nom", editorSceneUI::createPrefabInputText)

                    if (button("Ajouter", Vec2(-1, 0))) {
                        level.addPrefab(Prefab(editorSceneUI.createPrefabInputText, SerializationFactory.copy(entity)))
                        closeCurrentPopup()
                    }
                }
            }
        }
    }

    private fun drawInfoEntityTextWindow(entity: Entity) {
        with(ImGui) {
            setNextWindowSizeConstraints(Vec2(), Vec2(500f, 500f))
            functionalProgramming.withWindow("Données de l'entité", editorSceneUI::showInfoEntityTextWindow, WindowFlags.AlwaysAutoResize.i) {
                ImGuiHelper.insertUITextFields(entity)
                separator()

                val components = entity.getCurrentState().getComponents()
                functionalProgramming.withItemWidth(Constants.defaultWidgetsWidth) {
                    combo("component", editorSceneUI::entityCurrentComponentIndex, components.map { ReflectionUtility.simpleNameOf(it).removeSuffix("Component") })
                }

                val component = components.elementAtOrNull(editorSceneUI.entityCurrentComponentIndex)
                if (component != null) {
                    functionalProgramming.withIndent {
                        val requiredComponent = component.javaClass.kotlin.findAnnotation<RequiredComponent>()
                        val incorrectComponent = let {
                            requiredComponent?.component?.forEach {
                                if (!entity.getStateOrDefault(editorSceneUI.entityCurrentStateIndex).hasComponent(it))
                                    return@let true
                            }
                            false
                        }
                        if (!incorrectComponent)
                            ImGuiHelper.insertUITextFields(component)
                        else {
                            ImGuiHelper.textColored(Color.RED, "Il manque le(s) component(s) :")
                            functionalProgramming.withIndent {
                                text("${requiredComponent!!.component.map { it.simpleName }}")
                            }
                        }
                    }
                }
            }
        }
    }
//endregion
}