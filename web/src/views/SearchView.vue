<template>
  <div class="space-y-6">
    <!-- Search Input -->
    <div class="bg-white rounded-lg shadow-md p-6">
      <div class="flex items-center space-x-4">
        <input
          v-model="searchQuery"
          @keyup.enter="search"
          type="text"
          placeholder="Search data..."
          class="flex-1 px-4 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <button
          @click="search"
          :disabled="isSearching"
          class="bg-blue-600 text-white px-6 py-2 rounded-md hover:bg-blue-700 disabled:bg-gray-400"
        >
          {{ isSearching ? 'Searching...' : 'Search' }}
        </button>
      </div>
    </div>

    <!-- Error Message -->
    <div v-if="errorMessage" class="bg-red-100 text-red-700 p-4 rounded-md">
      {{ errorMessage }}
    </div>

    <!-- Results Section -->
    <div v-if="results.length" class="space-y-6">
      <div class="flex justify-between items-center text-sm text-gray-600">
        <p>Showing {{ results.length }} of {{ total }} results</p>
        <p>Page {{ currentPage }} of {{ totalPages }}</p>
      </div>

      <!-- Results Grid -->
      <div class="grid gap-6 md:grid-cols-2">
        <div
          v-for="(result, index) in results"
          :key="index"
          class="bg-white p-6 rounded-lg shadow-md hover:shadow-lg transition-shadow border border-gray-100"
        >
          <!-- Header -->
          <div class="flex justify-between items-start mb-4">
            <h3 class="text-lg font-semibold text-gray-800 truncate">
              {{ result.query || 'No query provided' }}
            </h3>
            <span
              class="text-xs px-2 py-1 rounded-full bg-blue-100 text-blue-800"
            >
              {{ result.category || 'Uncategorized' }}
            </span>
          </div>

          <!-- URL and Click Info -->
          <div class="space-y-3">
            <a
              :href="result.clicked_url || '#'"
              target="_blank"
              class="text-blue-600 hover:underline text-sm truncate block"
            >
              {{ result.clicked_url || 'No URL available' }}
            </a>
            <div class="flex items-center text-xs text-gray-500">
              <svg class="w-4 h-4 mr-1" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <span>Clicked: {{ formatDate(result.click_timestamp) || 'Not clicked' }}</span>
            </div>
          </div>

          <!-- Additional Details -->
          <dl class="mt-4 grid grid-cols-2 gap-2 text-sm text-gray-600">
            <div>
              <dt class="font-medium text-gray-700">Search Time</dt>
              <dd>{{ formatDate(result.search_timestamp) || 'N/A' }}</dd>
            </div>
            <div>
              <dt class="font-medium text-gray-700">Duration</dt>
              <dd>{{ result.search_duration_ms ? `${result.search_duration_ms} ms` : 'N/A' }}</dd>
            </div>
            <div>
              <dt class="font-medium text-gray-700">Results Count</dt>
              <dd>{{ formatResultsCount(result.results_count) }}</dd>
            </div>
            <div>
              <dt class="font-medium text-gray-700">Device</dt>
              <dd>{{ result.device_type ? `${result.device_type} (${result.browser || 'Unknown'})` : 'N/A' }}</dd>
            </div>
            <div>
              <dt class="font-medium text-gray-700">Location</dt>
              <dd>{{ result.user_location || 'Unknown' }}</dd>
            </div>
            <div>
              <dt class="font-medium text-gray-700">User ID</dt>
              <dd class="truncate">{{ result.user_id || 'N/A' }}</dd>
            </div>
          </dl>
        </div>
      </div>

      <!-- Pagination -->
      <div class="flex justify-center space-x-2">
        <button
          @click="changePage(currentPage - 1)"
          :disabled="currentPage === 1 || isSearching"
          class="px-4 py-2 border rounded-md disabled:text-gray-400 disabled:border-gray-200 hover:bg-gray-50"
        >
          Previous
        </button>
        <button
          @click="changePage(currentPage + 1)"
          :disabled="currentPage === totalPages || isSearching"
          class="px-4 py-2 border rounded-md disabled:text-gray-400 disabled:border-gray-200 hover:bg-gray-50"
        >
          Next
        </button>
      </div>
    </div>

    <!-- No Results -->
    <div v-else-if="!isSearching && searchQuery" class="text-center text-gray-600 py-8">
      No results found for "{{ searchQuery }}"
    </div>
  </div>
</template>

<script>
export default {
  name: 'SearchView',
  data() {
    return {
      searchQuery: '',
      results: [],
      total: 0,
      currentPage: 1,
      pageSize: 10,
      isSearching: false,
      errorMessage: ''
    }
  },
  computed: {
    totalPages() {
      return Math.ceil(this.total / this.pageSize)
    }
  },
  methods: {
    async search() {
      if (!this.searchQuery.trim()) return

      this.isSearching = true
      this.errorMessage = ''

      try {
        const response = await fetch('http://localhost:9999/data/search', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            query: this.searchQuery,
            page: this.currentPage,
            size: this.pageSize
          })
        })

        if (!response.ok) {
          const errorText = await response.text()
          throw new Error(`Search failed: ${response.status} - ${errorText}`)
        }

        const data = await response.json()

        if (data.status === 'success') {
          this.results = data.results || []
          this.total = data.total || 0
          this.currentPage = data.page || 1
          this.pageSize = data.size || 10
          console.log('Search results:', this.results)
        } else {
          throw new Error(data.message || 'Unexpected response status')
        }
      } catch (error) {
        console.error('Search error:', error)
        this.errorMessage = error.message || 'Search failed'
        this.results = []
      } finally {
        this.isSearching = false
      }
    },
    changePage(newPage) {
      if (newPage >= 1 && newPage <= this.totalPages) {
        this.currentPage = newPage
        this.search()
      }
    },
    formatDate(timestamp) {
      if (!timestamp) return null
      try {
        return new Date(timestamp).toLocaleString('en-US', {
          dateStyle: 'medium',
          timeStyle: 'short'
        })
      } catch (e) {
        console.warn(`Invalid timestamp: ${timestamp}`, e)
        return 'Invalid date'
      }
    },
    formatResultsCount(count) {
      if (count === undefined || count === null) return 'N/A'
      return Number(count).toLocaleString()
    }
  }
}
</script>

