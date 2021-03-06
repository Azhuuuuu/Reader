package org.swdc.reader.core.locators;

import javafx.scene.image.Image;
import lombok.extern.apachecommons.CommonsLog;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.swdc.reader.core.BookLocator;
import org.swdc.reader.core.configs.PDFConfig;
import org.swdc.reader.core.event.ContentItemFoundEvent;
import org.swdc.reader.entity.Book;
import org.swdc.reader.entity.ContentsItem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 处理pdf文档的解析
 */
@CommonsLog
public class PDFLocator implements BookLocator<Image> {

    private Book bookEntity;

    private String chapter = "";

    private PDDocument document;

    private PDFRenderer renderer;

    private Integer location = -1;

    private PDFConfig config;

    private Map<Integer, Image> locationDataMap = new WeakHashMap<>();

    private Boolean available;

    public PDFLocator(Book book, PDFConfig config) {
        this.config = config;
        this.bookEntity = book;
        File file = new File("data/library/" + book.getName());
        try {
            this.document = PDDocument.load(file);
            this.renderer = new PDFRenderer(document);
            if (bookEntity.getContentsItems() == null || bookEntity.getContentsItems().size() == 0) {
                this.loadOutLines();
            }
            this.available = true;
        } catch (IOException e) {
           log.error(e);
        }
    }

    private void loadOutLines() throws IOException {
        PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
        if (outline == null) {
            return;
        }
        PDOutlineItem item = outline.getFirstChild();
        if (item == null) {
            return;
        }
        loadContentTree(item);
    }

    private void loadContentTree(PDOutlineItem item) throws IOException {
        if(item.getDestination() instanceof PDPageDestination) {
            PDPageDestination  destination = (PDPageDestination) item.getDestination();
            String title = item.getTitle();
            Integer location = document.getPages().indexOf(destination.getPage());
            ContentsItem contentsItem = new ContentsItem();
            contentsItem.setLocated(bookEntity);
            contentsItem.setLocation(location + "");
            contentsItem.setTitle(title == null ? "Page：" + location : title);
            config.emit(new ContentItemFoundEvent(contentsItem,config));
            if (item.hasChildren()) {
                this.loadContentTree(item.getFirstChild());
            }
            if (item.getNextSibling() != null) {
                item = item.getNextSibling();
                this.loadContentTree(item);
            }
        }
    }

    @Override
    public Image prevPage() {
        this.location --;
        if (this.location < 0) {
            return nextPage();
        }
        return renderPage(this.location);
    }

    @Override
    public Image nextPage() {
        if (locationDataMap.size() > config.getRenderMapSize()) {
            locationDataMap.clear();
        }
        if (location + 1 >= document.getNumberOfPages()) {
            return  renderPage(this.location);
        }
        this.location ++;
        return renderPage(this.location);
    }

    @Override
    public Image toPage(String location) {
        try {
            Integer page = Integer.valueOf(location);
            this.location = page;
            return renderPage(page);
        } catch (Exception e) {
            log.error(e);
        }
        return null;
    }

    @Override
    public String getTitle() {
        return chapter;
    }

    @Override
    public String getLocation() {
        return location + "";
    }

    @Override
    public Image currentPage() {
        return renderPage(location);
    }

    @Override
    public void finalizeResources() {
        try {
            document.close();
            if (locationDataMap != null) {
                locationDataMap.clear();
                locationDataMap = null;
                this.available = false;
            }
        } catch (IOException e) {
            log.error(e);
        }
    }

    @Override
    public Boolean isAvailable() {
        return this.available;
    }

    private Image renderPage(Integer page) {
        try {
            if (locationDataMap.containsKey(page) && locationDataMap.get(page) != null) {
                return locationDataMap.get(page);
            }
            BufferedImage image = renderer.renderImage(page, config.getRenderQuality());
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ImageIO.write(image,"png",byteArrayOutputStream);
            Image data = new Image(new ByteArrayInputStream(byteArrayOutputStream.toByteArray()));
            locationDataMap.put(page, data);
            return data;
        } catch (Exception ex) {
            log.error(ex);
        } catch (OutOfMemoryError error) {
            locationDataMap.clear();
            System.gc();
            return renderPage(page);
        }
        return null;
    }
}
