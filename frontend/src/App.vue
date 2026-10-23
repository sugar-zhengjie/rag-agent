<script setup>
import { onMounted, ref } from 'vue'

const health = ref(null)
const documents = ref([])
const selectedId = ref('')
const chunks = ref([])
const question = ref('差旅报销需要什么材料？')
const chatQuestion = ref('差旅报销需要什么材料？')
const topK = ref(5)
const busy = ref(false)
const error = ref('')
const trace = ref(null)
const answer = ref('')
const evalCases = ref([])
const evalCaseId = ref('')
const evalReport = ref(null)
const evalScore = ref(null)
const sessionId = ref(sessionStorage.getItem('rag-session') || crypto.randomUUID())
sessionStorage.setItem('rag-session', sessionId.value)
const userId = ref(localStorage.getItem('rag-user') || 'owner')
const users = ref([])
const datasets = ref([])
const datasetId = ref('')
const datasetName = ref('')
const datasetPermission = ref('only_me')
const shareWithOther = ref(false)

function apiFetch(url, options = {}) {
  return fetch(url, {
    ...options,
    headers: { 'X-User-Id': userId.value, ...(options.headers || {}) }
  })
}

function otherUserId() {
  return userId.value === 'owner' ? 'member' : 'owner'
}

async function readError(response) {
  try {
    const body = await response.json()
    return body.message || response.statusText
  } catch {
    return response.statusText
  }
}

async function loadHealth() {
  const response = await apiFetch('/api/health')
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  health.value = await response.json()
}

async function loadDocuments() {
  const response = await apiFetch('/api/documents')
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  documents.value = await response.json()
}

async function openDocument(docId) {
  selectedId.value = docId
  const response = await apiFetch(`/api/documents/${docId}/chunks`)
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  chunks.value = await response.json()
}

async function upload(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  error.value = ''
  busy.value = true
  try {
    if (!datasetId.value) {
      error.value = '请先创建并选择知识库'
      return
    }
    const body = new FormData()
    body.append('file', file)
    body.append('datasetId', datasetId.value)
    const response = await apiFetch('/api/documents', { method: 'POST', body })
    if (!response.ok) throw new Error(await readError(response))
    const created = await response.json()
    await loadDocuments()
    await openDocument(created.docId)
  } catch (ex) {
    error.value = ex.message
  } finally {
    busy.value = false
  }
}

async function removeDocument(docId) {
  error.value = ''
  const response = await apiFetch(`/api/documents/${docId}`, { method: 'DELETE' })
  if (!response.ok && response.status !== 204) {
    error.value = await readError(response)
    return
  }
  if (selectedId.value === docId) {
    selectedId.value = ''
    chunks.value = []
  }
  await loadDocuments()
}

async function retrieve() {
  error.value = ''
  answer.value = ''
  busy.value = true
  try {
    const response = await apiFetch('/api/retrieve', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: question.value, sessionId: sessionId.value, topK: Number(topK.value) })
    })
    if (!response.ok) throw new Error(await readError(response))
    trace.value = await response.json()
  } catch (ex) {
    error.value = ex.message
  } finally {
    busy.value = false
  }
}

async function chat() {
  error.value = ''
  busy.value = true
  try {
    const response = await apiFetch('/api/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question: chatQuestion.value, sessionId: sessionId.value, topK: Number(topK.value) })
    })
    if (!response.ok) throw new Error(await readError(response))
    const body = await response.json()
    answer.value = body.answer
    trace.value = body.trace
  } catch (ex) {
    error.value = ex.message
  } finally {
    busy.value = false
  }
}

async function loadEvalCases() {
  const response = await apiFetch('/api/eval/dataset')
  if (!response.ok) {
    throw new Error(await readError(response))
  }
  evalCases.value = await response.json()
  if (!evalCaseId.value && evalCases.value.length) {
    evalCaseId.value = evalCases.value[0].id
  }
}

async function runEval() {
  error.value = ''
  busy.value = true
  evalScore.value = null
  try {
    const response = await apiFetch('/api/eval/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ topK: Number(topK.value), generate: false })
    })
    if (!response.ok) throw new Error(await readError(response))
    evalReport.value = await response.json()
  } catch (ex) {
    error.value = ex.message
  } finally {
    busy.value = false
  }
}

async function scoreCurrent() {
  error.value = ''
  busy.value = true
  try {
    const ranked = trace.value?.reranked || []
    const context = trace.value?.compressed || []
    const response = await apiFetch('/api/eval/score', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        caseId: evalCaseId.value,
        ranked: ranked.map(hit => hit.content),
        context: context.map(hit => hit.content),
        metadataTexts: ranked.map(hit => [hit.fileName, hit.heading, hit.content].filter(Boolean).join('\n')),
        answer: answer.value || null,
        topK: Number(topK.value)
      })
    })
    if (!response.ok) throw new Error(await readError(response))
    evalScore.value = await response.json()
  } catch (ex) {
    error.value = ex.message
  } finally {
    busy.value = false
  }
}

function formatMetric(value) {
  return value === null || value === undefined ? '—' : Number(value).toFixed(2)
}

function permissionLabel(permission) {
  if (permission === 'all_team_members') return '全员可见'
  if (permission === 'partial_members') return '指定成员'
  return '仅自己'
}

async function loadUsers() {
  const response = await apiFetch('/api/users')
  if (!response.ok) throw new Error(await readError(response))
  users.value = await response.json()
}

async function loadDatasets() {
  const response = await apiFetch('/api/datasets')
  if (!response.ok) throw new Error(await readError(response))
  datasets.value = await response.json()
  if (!datasets.value.some(item => item.id === datasetId.value)) {
    datasetId.value = datasets.value[0]?.id || ''
  }
}

async function changeUser() {
  localStorage.setItem('rag-user', userId.value)
  selectedId.value = ''
  chunks.value = []
  trace.value = null
  answer.value = ''
  await loadDatasets()
  await loadDocuments()
}

async function createDataset() {
  error.value = ''
  const memberIds = datasetPermission.value === 'partial_members' && shareWithOther.value ? [otherUserId()] : []
  const response = await apiFetch('/api/datasets', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: datasetName.value, permission: datasetPermission.value, memberIds })
  })
  if (!response.ok) {
    error.value = await readError(response)
    return
  }
  datasetName.value = ''
  const created = await response.json()
  await loadDatasets()
  datasetId.value = created.id
}

async function updateDataset(dataset) {
  const response = await apiFetch(`/api/datasets/${dataset.id}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ permission: dataset.permission, memberIds: dataset.memberIds })
  })
  if (!response.ok) {
    error.value = await readError(response)
    return
  }
  await loadDatasets()
  await loadDocuments()
}

async function resetSession() {
  await apiFetch(`/api/sessions/${sessionId.value}`, { method: 'DELETE' })
  sessionId.value = crypto.randomUUID()
  sessionStorage.setItem('rag-session', sessionId.value)
  answer.value = ''
}

onMounted(async () => {
  try {
    await loadHealth()
    await loadUsers()
    await loadDatasets()
    await loadDocuments()
    await loadEvalCases()
  } catch (ex) {
    error.value = ex.message
  }
})
</script>

<template>
  <div class="app">
    <header class="topbar">
      <div>
        <h1>RagAgent</h1>
        <div class="muted">上传文档，查看切片，对照 BM25、向量、RRF 和精排分数。</div>
      </div>
      <div class="pills" v-if="health">
        <span v-if="health.indexBackend === 'local'" class="pill ok">本地倒排</span>
        <span v-else class="pill" :class="health.elasticsearch ? 'ok' : 'bad'">ES {{ health.elasticsearch ? '已连接' : '未连接' }}</span>
        <span class="pill" :class="health.indexReady ? 'ok' : 'bad'">索引 {{ health.indexReady ? '就绪' : '未建' }}</span>
        <span class="pill" :class="health.apiKeyConfigured ? 'ok' : 'bad'">API Key {{ health.apiKeyConfigured ? '已配置' : '缺失' }}</span>
        <span class="pill">{{ health.embeddingModel }} / {{ health.dimensions }} 维</span>
        <span class="pill">{{ health.chatModel }} · {{ health.rerankModel }}</span>
      </div>
    </header>

    <div class="layout">
      <aside class="side">
        <label>
          当前用户
          <select v-model="userId" @change="changeUser">
            <option v-for="user in users" :key="user.id" :value="user.id">{{ user.name }}</option>
          </select>
        </label>
        <div class="doc-list">
          <label v-for="dataset in datasets" :key="dataset.id" class="doc" :class="{ active: dataset.id === datasetId }">
            <input type="radio" name="dataset" :value="dataset.id" v-model="datasetId" />
            {{ dataset.name }}
            <small>{{ permissionLabel(dataset.permission) }}</small>
            <select v-if="dataset.manageable" :value="dataset.permission" @change="dataset.permission = $event.target.value; updateDataset(dataset)">
              <option value="only_me">仅自己</option>
              <option value="all_team_members">全员</option>
              <option value="partial_members">指定成员</option>
            </select>
          </label>
          <div v-if="datasets.length === 0" class="muted">还没有可见的知识库</div>
        </div>
        <div class="row">
          <input v-model="datasetName" placeholder="新知识库名称" />
          <select v-model="datasetPermission">
            <option value="only_me">仅自己</option>
            <option value="all_team_members">全员</option>
            <option value="partial_members">指定成员</option>
          </select>
          <label v-if="datasetPermission === 'partial_members'">
            <input type="checkbox" v-model="shareWithOther" /> 授权另一位用户
          </label>
          <button class="ghost" :disabled="busy || !datasetName.trim()" @click="createDataset">创建知识库</button>
        </div>
        <label class="uploader">
          <input type="file" accept=".pdf,.doc,.docx,.ppt,.pptx,.xls,.xlsx,.html,.htm,.md,.txt,.csv" :disabled="busy" @change="upload" />
          <span class="muted">上传到所选知识库。pdf、Office、html、md、txt、csv，最大 50MB</span>
        </label>
        <div class="doc-list">
          <button
            v-for="doc in documents"
            :key="doc.docId"
            class="doc"
            :class="{ active: doc.docId === selectedId }"
            @click="openDocument(doc.docId)"
          >
            {{ doc.fileName }}
            <small>{{ doc.chunkCount }} 个切片 · {{ doc.fileType }}</small>
          </button>
          <div v-if="documents.length === 0" class="muted">还没有文档</div>
        </div>
      </aside>

      <main class="main">
        <p v-if="error" class="error">{{ error }}</p>

        <section class="panel" v-if="selectedId">
          <div class="row" style="justify-content: space-between;">
            <h2>切片预览</h2>
            <button class="ghost" @click="removeDocument(selectedId)">删除这篇文档</button>
          </div>
          <div class="chunks">
            <article v-for="chunk in chunks" :key="chunk.chunkId" class="chunk">
              <div class="meta">
                <span>#{{ chunk.chunkIndex }} {{ chunk.heading || chunk.sheet || (chunk.page ? `第 ${chunk.page} 页` : '正文') }}</span>
                <span>{{ chunk.content.length }} 字</span>
              </div>
              <div>{{ chunk.content }}</div>
            </article>
          </div>
        </section>

        <div class="work">
          <section class="panel">
            <h2>检索调试</h2>
            <textarea v-model="question" placeholder="输入检索问题"></textarea>
            <div class="row">
              <label>Top K <input v-model.number="topK" type="number" min="1" max="20" style="width: 72px" /></label>
              <button :disabled="busy" @click="retrieve">只检索</button>
            </div>
          </section>

          <section class="panel">
            <h2>问答</h2>
            <textarea v-model="chatQuestion" placeholder="输入问题，会带上会话上下文"></textarea>
            <div class="row">
              <button :disabled="busy" @click="chat">提问</button>
              <button class="ghost" @click="resetSession">清空会话</button>
            </div>
            <p class="muted">会话 {{ sessionId.slice(0, 8) }}</p>
            <div v-if="answer" class="answer">{{ answer }}</div>
          </section>
        </div>

        <section class="panel" v-if="trace">
          <h2>召回轨迹</h2>
          <p v-if="trace.warning" class="warning">{{ trace.warning }}</p>
          <p>改写后查询：{{ trace.rewrittenQuery }}</p>
          <div class="stages">
            <div>
              <h3>BM25</h3>
              <HitList :hits="trace.bm25" />
            </div>
            <div>
              <h3>kNN</h3>
              <HitList :hits="trace.knn" />
            </div>
            <div>
              <h3>RRF</h3>
              <HitList :hits="trace.rrf" />
            </div>
            <div>
              <h3>精排</h3>
              <HitList :hits="trace.reranked" />
            </div>
          </div>
          <h3>送入模型的上下文</h3>
          <p class="muted">{{ trace.promptLayout || "按相关度" }}。高置信度在前，同一文档保持原文顺序，低置信度单独放在后面。</p>
          <HitList :hits="trace.compressed || []" />
        </section>

        <section class="panel">
          <h2>分开评估</h2>
          <p class="muted">检索和生成分开打分。规则分不是事实裁判。先上传 samples/eval-corpus.md，再跑检索评估。</p>
          <div class="row">
            <button :disabled="busy" @click="runEval">跑检索评估</button>
            <label v-if="evalCases.length">
              样本
              <select v-model="evalCaseId">
                <option v-for="item in evalCases" :key="item.id" :value="item.id">{{ item.id }} {{ item.question }}</option>
              </select>
            </label>
            <button class="ghost" :disabled="busy || !trace || !evalCaseId" @click="scoreCurrent">给当前结果打分</button>
          </div>
          <p v-if="evalReport" class="muted">{{ evalReport.judgeNote }}</p>
          <div v-if="evalReport" class="stages">
            <div>
              <h3>检索</h3>
              <p>Hit Rate {{ formatMetric(evalReport.retrieval.hitRate) }} · MRR {{ formatMetric(evalReport.retrieval.mrr) }}</p>
              <p>Context Recall {{ formatMetric(evalReport.retrieval.contextRecall) }} · Context Precision {{ formatMetric(evalReport.retrieval.contextPrecision) }}</p>
            </div>
            <div>
              <h3>生成</h3>
              <p>Faithfulness {{ formatMetric(evalReport.generation.faithfulness) }} · Relevancy {{ formatMetric(evalReport.generation.answerRelevancy) }}</p>
              <p>Citation {{ formatMetric(evalReport.generation.citationAccuracy) }}</p>
              <p class="muted">检索 P95 {{ evalReport.engineering.p95RetrievalMs ?? '—' }} ms · 精排 P95 {{ evalReport.engineering.p95RerankMs ?? '—' }} ms · 估算 Token {{ evalReport.engineering.estimatedContextTokens }}</p>
            </div>
          </div>
          <div v-if="evalReport" class="hits">
            <article v-for="item in evalReport.results.filter(row => row.retrievalStage === '有问题').slice(0, 8)" :key="item.id" class="hit">
              <div class="meta"><span>{{ item.id }} {{ item.question }}</span><span>检索 {{ item.retrievalStage }}</span></div>
              <div>{{ item.retrievalNotes.join('；') }}</div>
            </article>
          </div>
          <article v-if="evalScore" class="hit">
            <div class="meta">
              <span>{{ evalScore.id }} 检索 {{ evalScore.retrievalStage }} · 生成 {{ evalScore.generationStage }}</span>
            </div>
            <p>Hit {{ formatMetric(evalScore.hitRate) }} · MRR {{ formatMetric(evalScore.mrr) }} · Recall {{ formatMetric(evalScore.contextRecall) }} · Precision {{ formatMetric(evalScore.contextPrecision) }}</p>
            <p>Faithfulness {{ formatMetric(evalScore.faithfulness) }} · Relevancy {{ formatMetric(evalScore.answerRelevancy) }} · Citation {{ formatMetric(evalScore.citationAccuracy) }}</p>
            <p class="muted">{{ [...evalScore.retrievalNotes, ...evalScore.generationNotes].join('；') }}</p>
          </article>
        </section>
      </main>
    </div>
  </div>
</template>

<script>
export default {
  components: {
    HitList: {
      props: { hits: { type: Array, default: () => [] } },
      template: `
        <div class="hits">
          <article v-for="(hit, index) in hits" :key="hit.chunkId + '-' + index" class="hit">
            <h4 v-if="hit.section && (index === 0 || hits[index - 1].section !== hit.section)">{{ hit.section }}</h4>
            <div class="meta">
              <span>{{ hit.fileName }}<template v-if="hit.page"> · 第 {{ hit.page }} 页</template><template v-if="hit.heading"> · {{ hit.heading }}</template><template v-if="hit.version"> · 版本 {{ hit.version }}</template></span>
              <span class="score">{{ Number(hit.score).toFixed(4) }}</span>
            </div>
            <p v-if="hit.conflictNote" class="warning">{{ hit.conflictNote }}</p>
            <div>{{ hit.content }}</div>
          </article>
          <div v-if="!hits.length" class="muted">无命中</div>
        </div>
      `
    }
  }
}
</script>
