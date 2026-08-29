package com.varisahayak.core.di;

import com.varisahayak.data.local.VariSahayakDatabase;
import com.varisahayak.data.local.dao.ResponderDao;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import dagger.internal.Provider;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

@ScopeMetadata
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
public final class DatabaseModule_ProvideResponderDaoFactory implements Factory<ResponderDao> {
  private final Provider<VariSahayakDatabase> dbProvider;

  private DatabaseModule_ProvideResponderDaoFactory(Provider<VariSahayakDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public ResponderDao get() {
    return provideResponderDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideResponderDaoFactory create(
      Provider<VariSahayakDatabase> dbProvider) {
    return new DatabaseModule_ProvideResponderDaoFactory(dbProvider);
  }

  public static ResponderDao provideResponderDao(VariSahayakDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideResponderDao(db));
  }
}
