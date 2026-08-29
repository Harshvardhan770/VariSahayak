package com.varisahayak.app;

import androidx.hilt.work.HiltWorkerFactory;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import javax.annotation.processing.Generated;

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
public final class VariSahayakApplication_MembersInjector implements MembersInjector<VariSahayakApplication> {
  private final Provider<HiltWorkerFactory> workerFactoryProvider;

  private VariSahayakApplication_MembersInjector(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    this.workerFactoryProvider = workerFactoryProvider;
  }

  @Override
  public void injectMembers(VariSahayakApplication instance) {
    injectWorkerFactory(instance, workerFactoryProvider.get());
  }

  public static MembersInjector<VariSahayakApplication> create(
      Provider<HiltWorkerFactory> workerFactoryProvider) {
    return new VariSahayakApplication_MembersInjector(workerFactoryProvider);
  }

  @InjectedFieldSignature("com.varisahayak.app.VariSahayakApplication.workerFactory")
  public static void injectWorkerFactory(VariSahayakApplication instance,
      HiltWorkerFactory workerFactory) {
    instance.workerFactory = workerFactory;
  }
}
