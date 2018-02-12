package be.catvert.pc.factories


import be.catvert.pc.actions.RemoveGOAction
import be.catvert.pc.builders.TweenBuilder
import be.catvert.pc.components.logics.LifeComponent
import be.catvert.pc.tweens.*

enum class TweenFactory(val tween: () -> Tween) {
    Empty({ EmptyTween() }),
    RemoveGO({ TweenBuilder(AlphaAtlasTween(0.5f, 0f), true).build(RemoveGOAction()) }),
    ReduceSize({ TweenBuilder(ResizeTween(0.5f), true).build() }),
    DisableLifeComponent({ TweenBuilder(DisableComponentTween(LifeComponent::class.java, 0.5f)).build() });

    operator fun invoke() = tween()
}