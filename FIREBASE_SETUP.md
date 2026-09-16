# Firebase per Sudoku Free

Progetto collegato: `sudoku-free-cc86a`  
Package Android: `com.quaderno.sudoku`

## 1. Abilitare accesso anonimo

Firebase Console → Authentication → Inizia → Metodo di accesso → Anonimo → Abilita → Salva.

## 2. Creare Cloud Firestore

Firebase Console → Firestore Database → Crea database → Modalità produzione.

## 3. Pubblicare le regole

Aprire Firestore Database → Regole, sostituire il contenuto con quello del file
`firestore.rules` presente nel progetto e premere Pubblica.

La classifica salva un solo record per utente anonimo e per data. Ogni utente può
modificare soltanto il proprio risultato e non può cancellare risultati.
