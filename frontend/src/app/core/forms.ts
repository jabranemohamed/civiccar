import type { FieldTree } from '@angular/forms/signals';

/**
 * Marque récursivement tous les champs d'un arbre Signal Forms comme touchés,
 * pour afficher les erreurs Material lors d'une soumission invalide (l'interop
 * n'affiche mat-error qu'à l'état touché + invalide).
 */
export function markAllTouched(field: FieldTree<unknown>): void {
  field().markAsTouched();
  for (const key of Object.keys(field)) {
    const child = (field as unknown as Record<string, FieldTree<unknown>>)[key];
    if (typeof child === 'function') {
      markAllTouched(child);
    }
  }
}
