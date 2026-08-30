package com.varisahayak.data.utils

import com.varisahayak.domain.model.UserRole
import com.varisahayak.domain.repository.BulkUserRequest
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.InputStream

object ExcelUserParser {

    fun parse(inputStream: InputStream): List<BulkUserRequest> {
        val workbook = WorkbookFactory.create(inputStream)
        val sheet = workbook.getSheetAt(0) ?: return emptyList()
        
        val requests = mutableListOf<BulkUserRequest>()
        val headerRow = sheet.getRow(0) ?: return emptyList()
        
        val colMap = mapHeaders(headerRow)
        
        // Skip header row
        for (i in 1..sheet.lastRowNum) {
            val row = sheet.getRow(i) ?: continue
            if (isRowEmpty(row)) continue
            
            val email = getCellValue(row, colMap["email"])?.trim() ?: ""
            val fullName = getCellValue(row, colMap["full_name"])?.trim() ?: ""
            val roleStr = getCellValue(row, colMap["role"])?.trim() ?: ""
            val organisation = getCellValue(row, colMap["organisation"])?.trim()
            val phone = getCellValue(row, colMap["phone"])?.trim()
            
            val role = UserRole.fromWire(roleStr) ?: UserRole.VOLUNTEER
            
            if (email.isNotEmpty() && fullName.isNotEmpty()) {
                requests.add(
                    BulkUserRequest(
                        email = email,
                        displayName = fullName,
                        role = role,
                        organisationName = organisation,
                        phone = phone,
                        rowNumber = i + 1
                    )
                )
            }
        }
        
        workbook.close()
        return requests
    }

    private fun mapHeaders(row: Row): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        for (cell in row) {
            val value = cell.stringCellValue.lowercase().trim()
            map[value] = cell.columnIndex
        }
        return map
    }

    private fun getCellValue(row: Row, index: Int?): String? {
        if (index == null) return null
        val cell = row.getCell(index) ?: return null
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue
            CellType.NUMERIC -> cell.numericCellValue.toLong().toString()
            else -> null
        }
    }
    
    private fun isRowEmpty(row: Row): Boolean {
        for (cell in row) {
            if (cell.cellType != CellType.BLANK) return false
        }
        return true
    }
}
