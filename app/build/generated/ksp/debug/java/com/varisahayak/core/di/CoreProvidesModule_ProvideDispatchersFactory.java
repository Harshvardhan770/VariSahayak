package com.varisahayak.core.di;

import com.varisahayak.core.common.DispatcherProvider;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation",
    "nullness:initialization.field.uninitialized"
})
public final class CoreProvidesModule_ProvideDispatchersFactory implements Factory<DispatcherProvider> {
  @Override
  public DispatcherProvider get() {
    return provideDispatchers();
  }

  public static CoreProvidesModule_ProvideDispatchersFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static DispatcherProvider provideDispatchers() {
    return Preconditions.checkNotNullFromProvides(CoreProvidesModule.INSTANCE.provideDispatchers());
  }

  private static final class InstanceHolder {
    static final CoreProvidesModule_ProvideDispatchersFactory INSTANCE = new CoreProvidesModule_ProvideDispatchersFactory();
  }
}
