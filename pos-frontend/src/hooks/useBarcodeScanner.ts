import { onMounted, onUnmounted } from 'vue';

export function useBarcodeScanner(onScanSuccess: (barcode: string) => void) {
    let barcodeBuffer = '';
    let lastKeyTime = Date.now();

    const handleKeyDown = (e: KeyboardEvent) => {
        const currentTime = Date.now();

        if (currentTime - lastKeyTime > 50) {
            barcodeBuffer = '';
        }
        lastKeyTime = currentTime;

        if (e.key === 'Enter') {
            if (barcodeBuffer.length > 5) {
                onScanSuccess(barcodeBuffer);
            }
            barcodeBuffer = '';
        }
        else if (e.key.length === 1) {
            barcodeBuffer += e.key;
        }
    };

    onMounted(() => window.addEventListener('keydown', handleKeyDown));
    onUnmounted(() => window.removeEventListener('keydown', handleKeyDown));
}