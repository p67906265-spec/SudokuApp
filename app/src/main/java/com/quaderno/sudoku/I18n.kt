package com.quaderno.sudoku

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

enum class AppLanguage(val code: String, val title: String) {
    SYSTEM("", "Automatica"), ITALIAN("it", "Italiano"), ENGLISH("en", "English"),
    SPANISH("es", "Español"), FRENCH("fr", "Français"), GERMAN("de", "Deutsch")
}

internal fun localizedContext(base: Context): Context {
    val code = base.getSharedPreferences("sudoku_settings", Context.MODE_PRIVATE).getString("language", "").orEmpty()
    val locale = if (code.isBlank()) base.resources.configuration.locales[0] else Locale.forLanguageTag(code)
    Locale.setDefault(locale)
    if (code.isBlank()) return base
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    return base.createConfigurationContext(config)
}

private val translations = mapOf(
    "en" to mapOf(
        "Gennaio" to "January", "Febbraio" to "February", "Marzo" to "March", "Aprile" to "April", "Maggio" to "May", "Giugno" to "June",
        "Luglio" to "July", "Agosto" to "August", "Settembre" to "September", "Ottobre" to "October", "Novembre" to "November", "Dicembre" to "December",
        "G I O C A" to "P L A Y", "R I P R E N D I" to "R E S U M E", "gioca, rilassati, divertiti" to "play, relax, have fun", "Apri" to "Open",
        "Impostazioni" to "Settings", "Statistiche" to "Statistics", "Come si gioca" to "How to play",
        "Sfide" to "Challenges", "Sfida un amico" to "Challenge a friend", "Scegli la tua sfida" to "Choose your challenge",
        "Ogni modalità mette alla prova un'abilità diversa." to "Each mode tests a different skill.",
        "Un nuovo schema ogni giorno, classifica e serie senza errori." to "A new puzzle every day, leaderboard and flawless streak.",
        "Completa lo schema bloccando un numero. Vince chi fa meno errori." to "Complete the puzzle by locking a number. Fewest mistakes wins.",
        "Crea o inserisci un codice e giocate sullo stesso schema." to "Create or enter a code and play the same puzzle.",
        "Sfida del giorno" to "Daily challenge", "Sfide con codice" to "Code challenges", "Gioca" to "Play",
        "Chiudi" to "Close", "Annulla" to "Cancel", "Continua" to "Continue", "Ricomincia" to "Restart",
        "Continua la partita" to "Continue game", "Cosa vuoi fare con la partita in corso?" to "What do you want to do with the current game?",
        "Sei sicuro di uscire?" to "Are you sure you want to quit?", "Sì" to "Yes", "No" to "No",
        "Tempo" to "Time", "Punteggio" to "Score", "Errori" to "Mistakes", "Difficoltà" to "Difficulty",
        "Classifica del giorno" to "Daily leaderboard", "Classifica" to "Leaderboard", "partecipanti" to "players", "Posizione" to "Position", "errori" to "mistakes",
        "Sfida Numero Bloccato" to "Locked Number Challenge", "SFIDA NUMERO BLOCCATO" to "LOCKED NUMBER CHALLENGE",
        "Completa lo schema usando solo i numeri bloccati. Gli errori non fermano la partita." to "Complete the puzzle using only locked numbers. Mistakes do not end the game.",
        "Sfida completata!" to "Challenge completed!", "Torna alle sfide" to "Back to challenges",
        "Mese completato" to "Month completed", "Sfide completate" to "Challenges completed",
        "FACILE" to "EASY", "MEDIO" to "MEDIUM", "DIFFICILE" to "HARD", "ESPERTO" to "EXPERT", "MASTER" to "MASTER", "ESTREMO" to "EXTREME",
        "Facile" to "Easy", "Medio" to "Medium", "Difficile" to "Hard", "Esperto" to "Expert", "Estremo" to "Extreme",
        "Animazioni" to "Animations", "Suggerimenti intelligenti" to "Smart hints", "Limite di 3 errori" to "3-mistake limit",
        "Nome in classifica" to "Leaderboard name", "Massimo 20 caratteri" to "Maximum 20 characters", "Giocatore" to "Player",
        "Lingua" to "Language", "Automatica" to "Automatic", "Tema schermata iniziale" to "Home screen theme", "Classico" to "Classic", "Onde pastello" to "Pastel waves", "Le preferenze vengono salvate e applicate subito." to "Preferences are saved and applied immediately.",
        "Annulla mossa" to "Undo", "Cancella" to "Erase", "Note" to "Notes", "Suggerimento" to "Hint", "Aiuto" to "Hint",
        "Partita in pausa" to "Game paused", "Tocca per continuare" to "Tap to continue", "Hai perso" to "Game over",
        "Riprova" to "Try again", "Esci" to "Exit", "Cambia schema" to "Change puzzle", "Hai completato il Sudoku!" to "You completed the Sudoku!",
        "Congratulazioni!" to "Congratulations!", "Preparati a giocare" to "Get ready to play", "Menu" to "Menu",
        "Crea una sfida" to "Create a challenge", "Genera un codice e invialo a chi vuoi sfidare." to "Generate a code and send it to anyone you want to challenge.",
        "GENERA CODICE" to "GENERATE CODE", "CODICE DELLA SFIDA" to "CHALLENGE CODE", "Inserisci il codice ricevuto da un amico." to "Enter the code received from a friend.",
        "Codice non valido" to "Invalid code", "GIOCA CON QUESTO CODICE" to "PLAY THIS CODE", "Schemi completati" to "Completed puzzles",
        "Non hai ancora completato schemi con un codice." to "You have not completed any code puzzles yet.", "Schema già completato" to "Puzzle already completed",
        "Gioca lo stesso schema" to "Play the same puzzle", "Rigioca" to "Play again", "Scegli il livello" to "Choose level",
        "Iniziate" to "Started", "Completate" to "Completed", "Abbandonate" to "Abandoned", "Tempo totale" to "Total time",
        "Serie attuale" to "Current streak", "Serie migliore" to "Best streak", "Dettaglio per livello" to "Details by level",
        "Livello ancora bloccato" to "Level still locked", "Tempo migliore" to "Best time", "Tempo medio" to "Average time",
        "Miglior punteggio" to "Best score", "Senza errori" to "No mistakes", "Prossimo" to "Next", "Inizia" to "Start", "Salta" to "Skip",
        "Un Sudoku si completa quando ogni numero da 1 a 9 appare una sola volta in ogni riga, colonna e riquadro 3×3." to "Complete the Sudoku so each number from 1 to 9 appears once in every row, column and 3×3 box.",
        "Seleziona una casella vuota e tocca un numero per inserirlo." to "Select an empty cell and tap a number to enter it.",
        "Attiva Note per aggiungere o rimuovere i possibili numeri nelle caselle." to "Turn on Notes to add or remove possible numbers in cells."
    ),
    "es" to mapOf(
        "Gennaio" to "Enero", "Febbraio" to "Febrero", "Marzo" to "Marzo", "Aprile" to "Abril", "Maggio" to "Mayo", "Giugno" to "Junio",
        "Luglio" to "Julio", "Agosto" to "Agosto", "Settembre" to "Septiembre", "Ottobre" to "Octubre", "Novembre" to "Noviembre", "Dicembre" to "Diciembre",
        "G I O C A" to "J U G A R", "R I P R E N D I" to "C O N T I N U A R", "gioca, rilassati, divertiti" to "juega, relájate, diviértete", "Apri" to "Abrir",
        "Impostazioni" to "Ajustes", "Statistiche" to "Estadísticas", "Come si gioca" to "Cómo jugar", "Sfide" to "Retos", "Sfida un amico" to "Reta a un amigo",
        "Scegli la tua sfida" to "Elige tu reto", "Ogni modalità mette alla prova un'abilità diversa." to "Cada modo pone a prueba una habilidad diferente.",
        "Un nuovo schema ogni giorno, classifica e serie senza errori." to "Un sudoku nuevo cada día, clasificación y racha sin errores.",
        "Completa lo schema bloccando un numero. Vince chi fa meno errori." to "Completa el sudoku bloqueando un número. Gana quien comete menos errores.",
        "Crea o inserisci un codice e giocate sullo stesso schema." to "Crea o introduce un código y jugad el mismo sudoku.", "Sfida del giorno" to "Reto diario",
        "Sfide con codice" to "Retos con código", "Gioca" to "Jugar", "Chiudi" to "Cerrar", "Annulla" to "Cancelar", "Continua" to "Continuar",
        "Ricomincia" to "Reiniciar", "Tempo" to "Tiempo", "Punteggio" to "Puntuación", "Errori" to "Errores", "Difficoltà" to "Dificultad",
        "Classifica del giorno" to "Clasificación diaria", "Classifica" to "Clasificación", "partecipanti" to "participantes", "Posizione" to "Posición", "errori" to "errores", "Sfida completata!" to "¡Reto completado!",
        "Sfida Numero Bloccato" to "Reto Número Bloqueado", "SFIDA NUMERO BLOCCATO" to "RETO NÚMERO BLOQUEADO",
        "Completa lo schema usando solo i numeri bloccati. Gli errori non fermano la partita." to "Completa el sudoku usando solo números bloqueados. Los errores no terminan la partida.",
        "Torna alle sfide" to "Volver a los retos", "Mese completato" to "Mes completado", "Sfide completate" to "Retos completados",
        "FACILE" to "FÁCIL", "MEDIO" to "MEDIO", "DIFFICILE" to "DIFÍCIL", "ESPERTO" to "EXPERTO", "ESTREMO" to "EXTREMO",
        "Animazioni" to "Animaciones", "Suggerimenti intelligenti" to "Ayudas inteligentes", "Limite di 3 errori" to "Límite de 3 errores",
        "Nome in classifica" to "Nombre en la clasificación", "Massimo 20 caratteri" to "Máximo 20 caracteres", "Giocatore" to "Jugador", "Lingua" to "Idioma", "Tema schermata iniziale" to "Tema de inicio", "Classico" to "Clásico", "Onde pastello" to "Ondas pastel",
        "Automatica" to "Automático", "Cancella" to "Borrar", "Note" to "Notas", "Suggerimento" to "Ayuda", "Aiuto" to "Ayuda", "Partita in pausa" to "Partida en pausa",
        "Tocca per continuare" to "Toca para continuar", "Hai perso" to "Has perdido", "Riprova" to "Intentar de nuevo", "Esci" to "Salir",
        "Crea una sfida" to "Crear un reto", "GENERA CODICE" to "GENERAR CÓDIGO", "CODICE DELLA SFIDA" to "CÓDIGO DEL RETO",
        "Codice non valido" to "Código no válido", "Schemi completati" to "Sudokus completados", "Scegli il livello" to "Elegir nivel",
        "Iniziate" to "Iniciadas", "Completate" to "Completadas", "Abbandonate" to "Abandonadas", "Tempo totale" to "Tiempo total",
        "Serie attuale" to "Racha actual", "Serie migliore" to "Mejor racha", "Tempo migliore" to "Mejor tiempo", "Tempo medio" to "Tiempo medio",
        "Miglior punteggio" to "Mejor puntuación", "Senza errori" to "Sin errores", "Prossimo" to "Siguiente", "Inizia" to "Empezar", "Salta" to "Saltar",
        "Un Sudoku si completa quando ogni numero da 1 a 9 appare una sola volta in ogni riga, colonna e riquadro 3×3." to "Completa el Sudoku para que cada número del 1 al 9 aparezca una vez en cada fila, columna y bloque 3×3.",
        "Seleziona una casella vuota e tocca un numero per inserirlo." to "Selecciona una casilla vacía y toca un número para introducirlo.",
        "Attiva Note per aggiungere o rimuovere i possibili numeri nelle caselle." to "Activa Notas para añadir o quitar números posibles."
    ),
    "fr" to mapOf(
        "Gennaio" to "Janvier", "Febbraio" to "Février", "Marzo" to "Mars", "Aprile" to "Avril", "Maggio" to "Mai", "Giugno" to "Juin",
        "Luglio" to "Juillet", "Agosto" to "Août", "Settembre" to "Septembre", "Ottobre" to "Octobre", "Novembre" to "Novembre", "Dicembre" to "Décembre",
        "G I O C A" to "J O U E R", "R I P R E N D I" to "R E P R E N D R E", "gioca, rilassati, divertiti" to "jouez, détendez-vous, amusez-vous", "Apri" to "Ouvrir",
        "Impostazioni" to "Paramètres", "Statistiche" to "Statistiques", "Come si gioca" to "Comment jouer", "Sfide" to "Défis", "Sfida un amico" to "Défier un ami",
        "Scegli la tua sfida" to "Choisissez votre défi", "Ogni modalità mette alla prova un'abilità diversa." to "Chaque mode met à l'épreuve une aptitude différente.",
        "Un nuovo schema ogni giorno, classifica e serie senza errori." to "Une nouvelle grille chaque jour, classement et série sans erreur.",
        "Completa lo schema bloccando un numero. Vince chi fa meno errori." to "Complétez la grille en verrouillant un nombre. Le moins d'erreurs gagne.",
        "Crea o inserisci un codice e giocate sullo stesso schema." to "Créez ou saisissez un code et jouez sur la même grille.", "Sfida del giorno" to "Défi du jour",
        "Sfide con codice" to "Défis avec code", "Gioca" to "Jouer", "Chiudi" to "Fermer", "Annulla" to "Annuler", "Continua" to "Continuer",
        "Ricomincia" to "Recommencer", "Tempo" to "Temps", "Punteggio" to "Score", "Errori" to "Erreurs", "Difficoltà" to "Difficulté",
        "Classifica del giorno" to "Classement du jour", "Classifica" to "Classement", "partecipanti" to "participants", "Posizione" to "Position", "errori" to "erreurs", "Sfida completata!" to "Défi terminé !",
        "Sfida Numero Bloccato" to "Défi Nombre Verrouillé", "SFIDA NUMERO BLOCCATO" to "DÉFI NOMBRE VERROUILLÉ",
        "Completa lo schema usando solo i numeri bloccati. Gli errori non fermano la partita." to "Complétez la grille uniquement avec les nombres verrouillés. Les erreurs n'arrêtent pas la partie.",
        "Torna alle sfide" to "Retour aux défis", "Mese completato" to "Mois terminé", "Sfide completate" to "Défis terminés",
        "FACILE" to "FACILE", "MEDIO" to "MOYEN", "DIFFICILE" to "DIFFICILE", "ESPERTO" to "EXPERT", "ESTREMO" to "EXTRÊME",
        "Animazioni" to "Animations", "Suggerimenti intelligenti" to "Aides intelligentes", "Limite di 3 errori" to "Limite de 3 erreurs",
        "Nome in classifica" to "Nom au classement", "Massimo 20 caratteri" to "20 caractères maximum", "Giocatore" to "Joueur", "Lingua" to "Langue", "Tema schermata iniziale" to "Thème de l'accueil", "Classico" to "Classique", "Onde pastello" to "Ondes pastel",
        "Automatica" to "Automatique", "Cancella" to "Effacer", "Note" to "Notes", "Suggerimento" to "Indice", "Aiuto" to "Aide", "Partita in pausa" to "Partie en pause",
        "Tocca per continuare" to "Touchez pour continuer", "Hai perso" to "Partie perdue", "Riprova" to "Réessayer", "Esci" to "Quitter",
        "Crea una sfida" to "Créer un défi", "GENERA CODICE" to "GÉNÉRER LE CODE", "CODICE DELLA SFIDA" to "CODE DU DÉFI",
        "Codice non valido" to "Code invalide", "Schemi completati" to "Grilles terminées", "Scegli il livello" to "Choisir le niveau",
        "Iniziate" to "Commencées", "Completate" to "Terminées", "Abbandonate" to "Abandonnées", "Tempo totale" to "Temps total",
        "Serie attuale" to "Série actuelle", "Serie migliore" to "Meilleure série", "Tempo migliore" to "Meilleur temps", "Tempo medio" to "Temps moyen",
        "Miglior punteggio" to "Meilleur score", "Senza errori" to "Sans erreur", "Prossimo" to "Suivant", "Inizia" to "Commencer", "Salta" to "Passer",
        "Un Sudoku si completa quando ogni numero da 1 a 9 appare una sola volta in ogni riga, colonna e riquadro 3×3." to "Complétez le Sudoku pour que chaque chiffre de 1 à 9 apparaisse une fois dans chaque ligne, colonne et bloc 3×3.",
        "Seleziona una casella vuota e tocca un numero per inserirlo." to "Sélectionnez une case vide et touchez un chiffre pour l'insérer.",
        "Attiva Note per aggiungere o rimuovere i possibili numeri nelle caselle." to "Activez les notes pour ajouter ou retirer les chiffres possibles."
    ),
    "de" to mapOf(
        "Gennaio" to "Januar", "Febbraio" to "Februar", "Marzo" to "März", "Aprile" to "April", "Maggio" to "Mai", "Giugno" to "Juni",
        "Luglio" to "Juli", "Agosto" to "August", "Settembre" to "September", "Ottobre" to "Oktober", "Novembre" to "November", "Dicembre" to "Dezember",
        "G I O C A" to "S P I E L E N", "R I P R E N D I" to "F O R T S E T Z E N", "gioca, rilassati, divertiti" to "spielen, entspannen, Spaß haben", "Apri" to "Öffnen",
        "Impostazioni" to "Einstellungen", "Statistiche" to "Statistiken", "Come si gioca" to "Spielanleitung", "Sfide" to "Aufgaben", "Sfida un amico" to "Freund herausfordern",
        "Scegli la tua sfida" to "Wähle deine Aufgabe", "Ogni modalità mette alla prova un'abilità diversa." to "Jeder Modus stellt eine andere Fähigkeit auf die Probe.",
        "Un nuovo schema ogni giorno, classifica e serie senza errori." to "Jeden Tag ein neues Rätsel, Rangliste und fehlerfreie Serie.",
        "Completa lo schema bloccando un numero. Vince chi fa meno errori." to "Löse das Rätsel mit einer gesperrten Zahl. Die wenigsten Fehler gewinnen.",
        "Crea o inserisci un codice e giocate sullo stesso schema." to "Erstelle oder gib einen Code ein und spielt dasselbe Rätsel.", "Sfida del giorno" to "Tagesaufgabe",
        "Sfide con codice" to "Code-Herausforderungen", "Gioca" to "Spielen", "Chiudi" to "Schließen", "Annulla" to "Abbrechen", "Continua" to "Weiter",
        "Ricomincia" to "Neu starten", "Tempo" to "Zeit", "Punteggio" to "Punkte", "Errori" to "Fehler", "Difficoltà" to "Schwierigkeit",
        "Classifica del giorno" to "Tagesrangliste", "Classifica" to "Rangliste", "partecipanti" to "Teilnehmer", "Posizione" to "Position", "errori" to "Fehler", "Sfida completata!" to "Aufgabe geschafft!",
        "Sfida Numero Bloccato" to "Gesperrte-Zahl-Aufgabe", "SFIDA NUMERO BLOCCATO" to "GESPERRTE-ZAHL-AUFGABE",
        "Completa lo schema usando solo i numeri bloccati. Gli errori non fermano la partita." to "Löse das Rätsel nur mit gesperrten Zahlen. Fehler beenden das Spiel nicht.",
        "Torna alle sfide" to "Zurück zu den Aufgaben", "Mese completato" to "Monat abgeschlossen", "Sfide completate" to "Aufgaben abgeschlossen",
        "FACILE" to "LEICHT", "MEDIO" to "MITTEL", "DIFFICILE" to "SCHWER", "ESPERTO" to "EXPERTE", "ESTREMO" to "EXTREM",
        "Animazioni" to "Animationen", "Suggerimenti intelligenti" to "Intelligente Hinweise", "Limite di 3 errori" to "3-Fehler-Grenze",
        "Nome in classifica" to "Name in der Rangliste", "Massimo 20 caratteri" to "Maximal 20 Zeichen", "Giocatore" to "Spieler", "Lingua" to "Sprache", "Tema schermata iniziale" to "Startseitenthema", "Classico" to "Klassisch", "Onde pastello" to "Pastellwellen",
        "Automatica" to "Automatisch", "Cancella" to "Löschen", "Note" to "Notizen", "Suggerimento" to "Hinweis", "Aiuto" to "Hilfe", "Partita in pausa" to "Spiel pausiert",
        "Tocca per continuare" to "Tippen zum Fortfahren", "Hai perso" to "Spiel verloren", "Riprova" to "Erneut versuchen", "Esci" to "Beenden",
        "Crea una sfida" to "Aufgabe erstellen", "GENERA CODICE" to "CODE ERSTELLEN", "CODICE DELLA SFIDA" to "AUFGABENCODE",
        "Codice non valido" to "Ungültiger Code", "Schemi completati" to "Abgeschlossene Rätsel", "Scegli il livello" to "Stufe wählen",
        "Iniziate" to "Begonnen", "Completate" to "Abgeschlossen", "Abbandonate" to "Abgebrochen", "Tempo totale" to "Gesamtzeit",
        "Serie attuale" to "Aktuelle Serie", "Serie migliore" to "Beste Serie", "Tempo migliore" to "Beste Zeit", "Tempo medio" to "Durchschnittszeit",
        "Miglior punteggio" to "Beste Punktzahl", "Senza errori" to "Fehlerfrei", "Prossimo" to "Weiter", "Inizia" to "Start", "Salta" to "Überspringen",
        "Un Sudoku si completa quando ogni numero da 1 a 9 appare una sola volta in ogni riga, colonna e riquadro 3×3." to "Vervollständige das Sudoku, sodass jede Zahl von 1 bis 9 in jeder Zeile, Spalte und jedem 3×3-Block einmal vorkommt.",
        "Seleziona una casella vuota e tocca un numero per inserirlo." to "Wähle ein leeres Feld und tippe auf eine Zahl.",
        "Attiva Note per aggiungere o rimuovere i possibili numeri nelle caselle." to "Aktiviere Notizen, um mögliche Zahlen einzutragen oder zu entfernen."
    )
)

internal fun tr(text: String): String = translations[Locale.getDefault().language]?.get(text) ?: text

internal fun weekdayLabels(): List<String> = when (Locale.getDefault().language) {
    "es" -> listOf("L", "M", "X", "J", "V", "S", "D")
    "fr" -> listOf("L", "M", "M", "J", "V", "S", "D")
    "de" -> listOf("M", "D", "M", "D", "F", "S", "S")
    "en" -> listOf("M", "T", "W", "T", "F", "S", "S")
    else -> listOf("L", "M", "M", "G", "V", "S", "D")
}
