package app.elolearning

import android.content.Context
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Forest = Color(0xFF8F92FF)
private val Ink = Color(0xFFF6F8FF)
private val Paper = Color(0xFF070B16)
private val Panel = Color(0xFF10182B)
private val Muted = Color(0xFF95A2BC)
private val ErrorRed = Color(0xFFFF778E)
private const val PREFS = "elolearning"

private data class StudyNote(
    val id: Long,
    val title: String,
    val subject: String,
    val grade: String,
    val content: String,
    val updatedAt: Long
)

private data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explanation: String
)

private enum class Page(val title: String) {
    STUDY("學習"), NOTES("我的重點"), QUIZ("我的題目"), SETTINGS("設定")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ELOLearningApp() }
    }
}

@Composable
private fun ELOLearningApp() {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var page by remember { mutableStateOf(Page.STUDY) }
    var notes by remember { mutableStateOf(loadNotes(context)) }
    var profileName by remember { mutableStateOf(preferences.getString("profile_name", "學習者").orEmpty()) }
    var apiKey by remember { mutableStateOf(ApiKeyStore.read(context)) }
    var topic by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("國文") }
    var grade by remember { mutableStateOf("國中一年級") }
    var question by remember { mutableStateOf("") }
    var selectedNote by remember { mutableStateOf<Long?>(null) }
    var editContent by remember(selectedNote) { mutableStateOf(notes.firstOrNull { it.id == selectedNote }?.content.orEmpty()) }
    var quiz by remember { mutableStateOf<List<QuizQuestion>>(emptyList()) }
    var answers by remember { mutableStateOf(emptyList<Int>()) }
    var submitted by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    fun saveNotes(updated: List<StudyNote>) {
        notes = updated.sortedByDescending(StudyNote::updatedAt)
        val array = JSONArray()
        notes.forEach { note ->
            array.put(
                JSONObject()
                    .put("id", note.id)
                    .put("title", note.title)
                    .put("subject", note.subject)
                    .put("grade", note.grade)
                    .put("content", note.content)
                    .put("updatedAt", note.updatedAt)
            )
        }
        preferences.edit().putString("notes", array.toString()).apply()
    }

    fun createBlankNote() {
        val now = System.currentTimeMillis()
        val note = StudyNote(now, "新筆記", "其他", "", "開始記錄你的學習內容。", now)
        saveNotes(listOf(note) + notes)
        selectedNote = note.id
        editContent = note.content
        page = Page.NOTES
    }

    fun createStudyNote() {
        val cleanTopic = topic.trim()
        if (cleanTopic.isBlank() || busy) return
        if (apiKey.isBlank()) {
            page = Page.SETTINGS
            error = "請先在設定中加入 Gemini API key。"
            return
        }
        busy = true
        error = null
        status = null
        scope.launch {
            runCatching {
                GeminiApi.generate(
                    apiKey = apiKey,
                    system = "你是 ELOLearning 的親切台灣老師。請使用繁體中文，依照${grade}學生程度解釋，內容正確易懂，使用清楚的小標題與例子。",
                    prompt = "科目：$subject\n年級：$grade\n主題：$cleanTopic\n學生問題：${question.trim().ifBlank { "請解釋概念並整理成可複習的重點。" }}\n\n請整理 3 到 6 個學習重點，補上必要例子或解題步驟。"
                )
            }.onSuccess { response ->
                val note = StudyNote(System.currentTimeMillis(), cleanTopic, subject.trim(), grade.trim(), response.trim(), System.currentTimeMillis())
                saveNotes(listOf(note) + notes)
                selectedNote = note.id
                editContent = note.content
                page = Page.NOTES
                status = "學習重點已保存。"
            }.onFailure { failure -> error = failure.message ?: "無法產生學習內容。" }
            busy = false
        }
    }

    fun createQuiz() {
        if (notes.isEmpty() || busy) return
        if (apiKey.isBlank()) {
            page = Page.SETTINGS
            error = "請先在設定中加入 Gemini API key。"
            return
        }
        busy = true
        error = null
        status = null
        scope.launch {
            val source = notes.take(10).joinToString("\n\n") { "【${it.subject}｜${it.title}】\n${it.content.take(1800)}" }
            runCatching {
                GeminiApi.generate(
                    apiKey = apiKey,
                    system = "你是 ELOLearning 的出題老師。使用繁體中文，只能依據學生筆記出題，不得加入筆記中沒有的知識。answer 是正確選項索引，從 0 開始。只回傳 JSON。",
                    prompt = "產生 5 題四選一題目。格式：{\"questions\":[{\"question\":\"題目\",\"options\":[\"A\",\"B\",\"C\",\"D\"],\"answer\":0,\"explanation\":\"解析\"}]}\n筆記：\n$source",
                    json = true
                )
            }.onSuccess { raw ->
                runCatching { parseQuiz(raw) }
                    .onSuccess { generated ->
                        quiz = generated
                        answers = List(generated.size) { -1 }
                        submitted = false
                        status = "題目已準備好。"
                    }
                    .onFailure { failure -> error = failure.message ?: "題目資料格式錯誤。" }
            }.onFailure { failure -> error = failure.message ?: "出題失敗。" }
            busy = false
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Forest, onPrimary = Color.White, background = Paper, surface = Panel, onSurface = Ink)) {
        Scaffold(
            containerColor = Paper,
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    Page.entries.forEach { item ->
                        NavigationBarItem(
                            selected = page == item,
                            onClick = { page = item; error = null },
                            icon = {
                                Icon(
                                    when (item) {
                                        Page.STUDY -> Icons.Default.Lightbulb
                                        Page.NOTES -> Icons.Default.EditNote
                                        Page.QUIZ -> Icons.Default.AutoStories
                                        Page.SETTINGS -> Icons.Default.Settings
                                    },
                                    contentDescription = null
                                )
                            },
                            label = { Text(item.title) }
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).background(Paper)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ELOLearning", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text("${profileName.ifBlank { "學習者" }} 的學習空間", color = Muted, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.SmartToy, contentDescription = "AI 學習助手", tint = Forest, modifier = Modifier.size(28.dp))
                }
                error?.let { MessageBanner(text = it, color = ErrorRed) }
                status?.let { MessageBanner(text = it, color = Forest) }
                when (page) {
                    Page.STUDY -> StudyPage(
                        topic = topic,
                        subject = subject,
                        grade = grade,
                        question = question,
                        busy = busy,
                        noteCount = notes.size,
                        onTopicChange = { topic = it },
                        onSubjectChange = { subject = it },
                        onGradeChange = { grade = it },
                        onQuestionChange = { question = it },
                        onGenerate = ::createStudyNote,
                        onOpenNotes = { page = Page.NOTES }
                    )
                    Page.NOTES -> NotesPage(
                        notes = notes,
                        selectedNote = selectedNote,
                        editContent = editContent,
                        onCreate = ::createBlankNote,
                        onSelect = { selectedNote = it.id; editContent = it.content },
                        onContentChange = { editContent = it },
                        onSave = {
                            val current = notes.firstOrNull { it.id == selectedNote }
                            if (current != null) {
                                saveNotes(notes.map { if (it.id == current.id) it.copy(content = editContent, updatedAt = System.currentTimeMillis()) else it })
                                status = "筆記已儲存。"
                            }
                        },
                        onDelete = {
                            saveNotes(notes.filterNot { it.id == selectedNote })
                            selectedNote = null
                            editContent = ""
                            status = "筆記已刪除。"
                        },
                        onBack = { selectedNote = null }
                    )
                    Page.QUIZ -> QuizPage(
                        noteCount = notes.size,
                        questions = quiz,
                        answers = answers,
                        submitted = submitted,
                        busy = busy,
                        onGenerate = ::createQuiz,
                        onAnswer = { index, value -> answers = answers.toMutableList().also { it[index] = value } },
                        onSubmit = { submitted = true }
                    )
                    Page.SETTINGS -> SettingsPage(
                        name = profileName,
                        apiKey = apiKey,
                        onNameChange = { profileName = it },
                        onKeyChange = { apiKey = it },
                        onSave = {
                            preferences.edit().putString("profile_name", profileName.trim()).apply()
                            ApiKeyStore.write(context, apiKey.trim())
                            apiKey = apiKey.trim()
                            status = "設定已保存。"
                            error = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StudyPage(
    topic: String,
    subject: String,
    grade: String,
    question: String,
    busy: Boolean,
    noteCount: Int,
    onTopicChange: (String) -> Unit,
    onSubjectChange: (String) -> Unit,
    onGradeChange: (String) -> Unit,
    onQuestionChange: (String) -> Unit,
    onGenerate: () -> Unit,
    onOpenNotes: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("今天想學什麼？", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("輸入主題，AI 會整理重點並保存到你的學習筆記。", color = Muted, fontSize = 14.sp)
        OutlinedTextField(value = topic, onValueChange = onTopicChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("學習主題") }, placeholder = { Text("例如：岳陽樓記、一次函數、細胞構造") })
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = grade, onValueChange = onGradeChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text("年級") })
            OutlinedTextField(value = subject, onValueChange = onSubjectChange, modifier = Modifier.weight(1f), singleLine = true, label = { Text("科目") })
        }
        OutlinedTextField(value = question, onValueChange = onQuestionChange, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 5, label = { Text("你想問 AI 什麼？") }, placeholder = { Text("例如：用生活例子說明，並列出解題步驟") })
        Button(onClick = onGenerate, enabled = topic.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest)) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("整理學習重點")
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenNotes)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoStories, contentDescription = null, tint = Forest)
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                    Text("我的學習重點", color = Ink, fontWeight = FontWeight.SemiBold)
                    Text("已保存 $noteCount 筆筆記", color = Muted, fontSize = 13.sp)
                }
                Text("查看", color = Forest, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun NotesPage(
    notes: List<StudyNote>,
    selectedNote: Long?,
    editContent: String,
    onCreate: () -> Unit,
    onSelect: (StudyNote) -> Unit,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (selectedNote == null) "我的重點" else "編輯重點", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("${notes.size} 筆", color = Muted, fontSize = 13.sp)
            if (selectedNote == null) IconButton(onClick = onCreate) { Icon(Icons.Default.Add, contentDescription = "新增筆記", tint = Forest) }
        }
        if (selectedNote == null) {
            if (notes.isEmpty()) EmptyState("還沒有學習重點", "先到「學習」整理一個主題，筆記會自動保存。")
            else androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notes.size) { index ->
                    val note = notes[index]
                    Card(modifier = Modifier.fillMaxWidth().clickable { onSelect(note) }, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text("${note.subject} · ${note.title}", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(note.grade, color = Forest, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
                            Text(note.content, color = Muted, fontSize = 13.sp, lineHeight = 19.sp, maxLines = 3, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        } else {
            val current = notes.firstOrNull { it.id == selectedNote }
            if (current == null) EmptyState("找不到這筆筆記", "")
            else {
                Text("${current.subject} · ${current.title}", color = Forest, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onBack) { Text("返回筆記列表") }
                OutlinedTextField(value = editContent, onValueChange = onContentChange, modifier = Modifier.weight(1f).fillMaxWidth(), minLines = 8, label = { Text("筆記內容") })
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 12.dp)) {
                    Button(onClick = onSave, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Forest), shape = RoundedCornerShape(8.dp)) { Text("儲存修改") }
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "刪除筆記", tint = ErrorRed) }
                }
            }
        }
    }
}

@Composable
private fun QuizPage(
    noteCount: Int,
    questions: List<QuizQuestion>,
    answers: List<Int>,
    submitted: Boolean,
    busy: Boolean,
    onGenerate: () -> Unit,
    onAnswer: (Int, Int) -> Unit,
    onSubmit: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("我的題目", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("從已保存的學習重點出題，交卷後查看答案與解析。", color = Muted, fontSize = 13.sp)
        Button(onClick = onGenerate, enabled = noteCount > 0 && !busy, colors = ButtonDefaults.buttonColors(containerColor = Forest), shape = RoundedCornerShape(8.dp)) {
            if (busy) CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Text(if (questions.isEmpty()) "產生練習題" else "重新出題")
        }
        if (noteCount == 0) EmptyState("需要先有學習重點", "先整理主題，AI 才能依照你的筆記出題。")
        questions.forEachIndexed { questionIndex, item ->
            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(8.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("${questionIndex + 1}. ${item.question}", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    item.options.forEachIndexed { optionIndex, option ->
                        val correct = submitted && optionIndex == item.answer
                        val wrong = submitted && answers.getOrElse(questionIndex) { -1 } == optionIndex && optionIndex != item.answer
                        Row(modifier = Modifier.fillMaxWidth().clickable(enabled = !submitted) { onAnswer(questionIndex, optionIndex) }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = answers.getOrElse(questionIndex) { -1 } == optionIndex, onClick = { if (!submitted) onAnswer(questionIndex, optionIndex) }, enabled = !submitted)
                            Text(option, color = when { correct -> Forest; wrong -> ErrorRed; else -> Ink }, fontSize = 14.sp)
                        }
                    }
                    if (submitted) Text("解析：${item.explanation}", color = Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        if (questions.isNotEmpty()) {
            if (submitted) {
                val score = questions.indices.count { answers.getOrElse(it) { -1 } == questions[it].answer }
                Text("本次答對 $score / ${questions.size} 題", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            } else Button(onClick = onSubmit, enabled = answers.any { it >= 0 }, colors = ButtonDefaults.buttonColors(containerColor = Forest), shape = RoundedCornerShape(8.dp)) { Text("交卷") }
        }
    }
}

@Composable
private fun SettingsPage(
    name: String,
    apiKey: String,
    onNameChange: (String) -> Unit,
    onKeyChange: (String) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("設定", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text("個人資料與 AI 老師連線", color = Muted, fontSize = 13.sp)
        OutlinedTextField(value = name, onValueChange = onNameChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("顯示名稱") })
        OutlinedTextField(value = apiKey, onValueChange = onKeyChange, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("Gemini API key") }, visualTransformation = PasswordVisualTransformation())
        Text("API key 會使用 Android Keystore 加密後保存在這台手機。AI 問題會傳送至 Google Gemini。", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Forest), shape = RoundedCornerShape(8.dp)) { Text("儲存設定") }
    }
}

@Composable
private fun MessageBanner(text: String, color: Color) {
    Text(text, color = color, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp))
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        if (detail.isNotBlank()) Text(detail, color = Muted, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 5.dp))
    }
}

private fun loadNotes(context: Context): List<StudyNote> = runCatching {
    val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("notes", "[]") ?: "[]"
    val array = JSONArray(raw)
    (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        StudyNote(item.getLong("id"), item.getString("title"), item.optString("subject", "其他"), item.optString("grade", ""), item.getString("content"), item.optLong("updatedAt"))
    }.sortedByDescending(StudyNote::updatedAt)
}.getOrDefault(emptyList())

private fun parseQuiz(raw: String): List<QuizQuestion> {
    val first = raw.indexOf('{').takeIf { it >= 0 } ?: error("AI 回覆缺少 JSON 物件")
    val last = raw.lastIndexOf('}').takeIf { it >= first } ?: error("AI 回覆 JSON 不完整")
    val array = JSONObject(raw.substring(first, last + 1)).optJSONArray("questions") ?: error("AI 回覆缺少 questions")
    val result = (0 until array.length()).map { index ->
        val item = array.getJSONObject(index)
        val choices = item.getJSONArray("options")
        val options = (0 until choices.length()).map(choices::getString)
        val answer = item.getInt("answer")
        require(options.size == 4 && answer in options.indices) { "選項或答案索引不正確" }
        QuizQuestion(item.getString("question"), options, answer, item.optString("explanation", ""))
    }
    require(result.isNotEmpty()) { "AI 沒有產生題目" }
    return result
}

private object GeminiApi {
    suspend fun generate(apiKey: String, system: String, prompt: String, json: Boolean = false): String = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "請先設定 Gemini API key。" }
        val payload = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("temperature", 0.25).put("maxOutputTokens", 3000).apply {
                if (json) put("responseMimeType", "application/json")
            })
        var attempt = 0
        var result: String? = null
        while (result == null) {
            val connection = java.net.URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent").openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 90000
            connection.doOutput = true
            connection.setRequestProperty("x-goog-api-key", apiKey)
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            try {
                connection.outputStream.use { it.write(payload.toString().toByteArray(StandardCharsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.use { input -> BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use(BufferedReader::readText) }.orEmpty()
                if (status == 503 && attempt < 2) {
                    val retryDelay = connection.getHeaderField("Retry-After")
                        ?.toLongOrNull()
                        ?.times(1_000)
                        ?.coerceIn(1_000, 10_000)
                        ?: (1_000L shl attempt)
                    attempt++
                    delay(retryDelay)
                    continue
                }
                if (status !in 200..299) error("Gemini 回應 $status：${body.take(320)}")
                val parts = JSONObject(body).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                result = (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }.trim().ifBlank { error("Gemini 沒有回傳內容。") }
            } finally {
                connection.disconnect()
            }
        }
        result ?: error("Gemini 沒有回傳內容。")
    }
}

private object ApiKeyStore {
    private const val ALIAS = "elolearning-gemini-key"
    private const val KEY = "gemini_key_encrypted"

    fun read(context: Context): String {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return ""
        return runCatching {
            val pieces = saved.split(':', limit = 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    fun write(context: Context, value: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (value.isBlank()) {
            prefs.edit().remove(KEY).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val result = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString(KEY, "${Base64.encodeToString(cipher.iv, Base64.NO_WRAP)}:${Base64.encodeToString(result, Base64.NO_WRAP)}").apply()
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return store.getKey(ALIAS, null) as? SecretKey ?: error("找不到 API key 加密金鑰")
    }

    private fun getOrCreateSecretKey(): SecretKey {
        runCatching { secretKey() }.getOrNull()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }
}
