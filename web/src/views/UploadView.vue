<template>
  <div class="bg-white rounded-lg shadow-md p-6">
    <h2 class="text-2xl font-medium text-gray-800 mb-6">Upload CSV File</h2>

    <div class="border-2 border-dashed border-gray-300 rounded-lg p-8 text-center">
      <input
        type="file"
        ref="fileInput"
        @change="handleFileSelect"
        accept=".csv"
        class="hidden"
      />
      <div v-if="!selectedFile" @click="$refs.fileInput.click()" class="cursor-pointer">
        <svg class="mx-auto h-12 w-12 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 0115.9 6 4 4 0 0116 14v2m-4-6v6m0 0l-3-3m3 3l3-3" />
        </svg>
        <p class="mt-1 text-sm text-gray-600">Click to upload a CSV file</p>
      </div>

      <div v-else class="space-y-4">
        <p class="text-gray-700">Selected file: {{ selectedFile.name }}</p>
        <div class="flex justify-center space-x-4">
          <button
            @click="uploadFile"
            :disabled="isUploading"
            class="bg-blue-600 text-white px-4 py-2 rounded-md hover:bg-blue-700 disabled:bg-gray-400"
          >
            {{ isUploading ? 'Uploading...' : 'Upload' }}
          </button>
          <button
            @click="clearFile"
            class="bg-gray-200 text-gray-700 px-4 py-2 rounded-md hover:bg-gray-300"
          >
            Cancel
          </button>
        </div>
      </div>
    </div>

    <div v-if="uploadMessage" class="mt-4 p-4 rounded-md" :class="{
      'bg-green-100 text-green-700': uploadStatus === 'success',
      'bg-red-100 text-red-700': uploadStatus === 'error'
    }">
      {{ uploadMessage }}
    </div>
  </div>
</template>

<script>
export default {
  name: 'UploadView',
  data() {
    return {
      selectedFile: null,
      isUploading: false,
      uploadMessage: '',
      uploadStatus: ''
    }
  },
  methods: {
    handleFileSelect(event) {
      this.selectedFile = event.target.files[0]
    },
    clearFile() {
      this.selectedFile = null
      this.uploadMessage = ''
      this.$refs.fileInput.value = ''
    },
    async uploadFile() {
      if (!this.selectedFile) return

      this.isUploading = true
      const formData = new FormData()
      formData.append('file', this.selectedFile)

      try {
        const response = await fetch('http://localhost:9999/data/file/import', {
          method: 'POST',
          body: formData
        })

        if (!response.ok) {
          throw new Error('Upload failed')
        }

        const data = await response.json()
        this.uploadStatus = data.status
        this.uploadMessage = data.message
        if (data.status === 'success') {
          setTimeout(() => this.clearFile(), 3000)
        }
      } catch (error) {
        this.uploadStatus = 'error'
        this.uploadMessage = error.message || 'Upload failed'
      } finally {
        this.isUploading = false
      }
    }
  }
}
</script>
