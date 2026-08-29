package com.varisahayak.core.di;

import com.varisahayak.data.local.VariSahayakDatabase;
import com.varisahayak.data.local.dao.IncidentEventDao;
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
public final class DatabaseModule_ProvideIncidentEventDaoFactory implements Factory<IncidentEventDao> {
  private final Provider<VariSahayakDatabase> dbProvider;

  private DatabaseModule_ProvideIncidentEventDaoFactory(Provider<VariSahayakDatabase> dbProvider) {
    this.dbProvider = dbProvider;
  }

  @Override
  public IncidentEventDao get() {
    return provideIncidentEventDao(dbProvider.get());
  }

  public static DatabaseModule_ProvideIncidentEventDaoFactory create(
      Provider<VariSahayakDatabase> dbProvider) {
    return new DatabaseModule_ProvideIncidentEventDaoFactory(dbProvider);
  }

  public static IncidentEventDao provideIncidentEventDao(VariSahayakDatabase db) {
    return Preconditions.checkNotNullFromProvides(DatabaseModule.INSTANCE.provideIncidentEventDao(db));
  }
}
