document.addEventListener('DOMContentLoaded', function() {
    chrome.storage.local.get(['researchNotes'], function(result) {
        if (result.researchNotes) {
            document.getElementById('notes').value = result.researchNotes;
        }
    });

    document.getElementById("summarizeBtn").addEventListener('click', () => processText('summarize'));
    document.getElementById("suggestBtn").addEventListener('click', () => processText('suggest'));
    document.getElementById("analyzeBtn").addEventListener('click', () => processText('analyze'));
    document.getElementById("saveNoteBtn").addEventListener('click', saveNote);
});

async function processText(operation) {
    try {
        const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
        const [result] = await chrome.scripting.executeScript({
            target: { tabId: tab.id },
            function: () => window.getSelection().toString()
        });

        if (!result || !result.result) {
            showResult('No text selected');
            return;
        }

        const response = await fetch('http://localhost:8080/api/research/process', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ content: result.result, operation })
        });

        if (!response.ok) {
            throw new Error(`API ERROR: ${response.status}`);
        }

        const text = await response.text();
        showResult(text.replace(/(?:\r\n|\r|\n)/g, '<br>'));

    } catch (error) {
        showResult(error.message);
    }
}

async function saveNote() {
    const notes = document.getElementById('notes').value;
    chrome.storage.local.set({ researchNotes: notes }, function() {
        alert('Note saved');
    });
}

function showResult(content) {
    document.getElementById('result').innerHTML = content;
    document.getElementById('result').style.display = 'block';
}
